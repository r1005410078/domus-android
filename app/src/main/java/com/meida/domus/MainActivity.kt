package com.meida.domus

import android.app.Activity
import android.content.ActivityNotFoundException
import android.net.Uri
import android.os.Bundle
import android.view.ViewGroup
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedDispatcher
import androidx.activity.addCallback
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            // "http://192.168.2.10:3001"
            WebViewSafeScreen(
                "http://192.168.2.10:3001",
//                "http://114.55.227.206:3000",
                onBack = { finish() },
                backPressedDispatcher = onBackPressedDispatcher
            )
        }

    }
}

@Composable
fun WebViewSafeScreen(
    url: String,
    onBack: () -> Unit,
    backPressedDispatcher: OnBackPressedDispatcher
) {
    val context = LocalContext.current
    var webViewRef by remember { mutableStateOf<WebView?>(null) }
    var filePathCallback by remember { mutableStateOf<ValueCallback<Array<Uri>>?>(null) }
    val activity = context as? ComponentActivity

    // 系统返回键处理
    DisposableEffect(backPressedDispatcher) {
        val callback = backPressedDispatcher.addCallback {
            if (webViewRef?.canGoBack() == true) {
                webViewRef?.goBack()
            } else {
                onBack()
            }
        }
        onDispose { callback.remove() }
    }


    // 1️⃣ 创建 launcher
    val launcher = rememberLauncherForActivityResult  (
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        // 2️⃣ 处理回调
        val data = result.data
        val uris = if (result.resultCode == Activity.RESULT_OK && data != null) {
            data.data?.let { arrayOf(it) }
        } else null
        filePathCallback?.onReceiveValue(uris)
        filePathCallback = null
    }

    AndroidView(
        modifier = Modifier
            .fillMaxSize()
            .padding(WindowInsets.safeDrawing.asPaddingValues()), // 安全区域
        factory = { context ->
            WebView(context).apply {
                // 注入 JS 修正 vh 高度
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )

                webViewClient = WebViewClient()
                webChromeClient = object : WebChromeClient() {

                    override fun onShowFileChooser(
                        webView: WebView?,
                        filePathCallbackParam: ValueCallback<Array<Uri>>?,
                        fileChooserParams: FileChooserParams?
                    ): Boolean {
                        filePathCallback = filePathCallbackParam
                        try {
                            val intent = fileChooserParams?.createIntent()
                            if (intent != null) {
                                launcher.launch(intent) // ✅ 使用新的 Activity Result API
                            }
                        } catch (e: ActivityNotFoundException) {
                            filePathCallback = null
                            return false
                        }
                        return true
                    }
                }

                settings.apply {
                    javaScriptEnabled = true                 // JS 支持
                    domStorageEnabled = true                 // DOM Storage / localStorage
                    allowFileAccess = true                   // 文件访问
                    allowContentAccess = true                // 内容访问
                    useWideViewPort = true                   // 支持 viewport
                    setGeolocationEnabled(true)              // 支持定位
                    allowFileAccess = true                   // 允许 WebView 访问本地文件系统（file://）
                    allowContentAccess = true                // 允许 WebView 访问 Content Provider 中的内容（content://）
                    useWideViewPort = true                   // 支持 viewport meta 标签，网页可自适应屏幕宽度
                    mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW // 混合内容
                    textZoom = 100
                    userAgentString = "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/133.0 Mobile Safari/537.36"
                }

                loadUrl(url)
                webViewRef = this
            }
        }
    )

    // 处理文件选择回调
    DisposableEffect(Unit) {
        onDispose {
            webViewRef?.destroy()
        }
    }
}