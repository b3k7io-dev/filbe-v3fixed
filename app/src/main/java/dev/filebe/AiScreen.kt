package dev.filebe

import android.net.Uri
import android.net.http.SslError
import android.webkit.CookieManager
import android.webkit.SslErrorHandler
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView

private const val GOOGLE_AI_MODE = "https://google.com/aimode"

@Composable fun AiScreen(state: UiState, vm: FilebeViewModel) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val clipboard = LocalClipboardManager.current
    var address by remember { mutableStateOf(GOOGLE_AI_MODE) }
    var pageLoading by remember { mutableStateOf(true) }
    var webView by remember { mutableStateOf<WebView?>(null) }
    var refreshKey by remember { mutableIntStateOf(0) }
    DisposableEffect(webView) {
        onDispose { webView?.stopLoading(); webView?.destroy(); webView = null }
    }
    BackHandler(enabled = webView?.canGoBack() == true) { webView?.goBack() }
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 2.dp), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            IconButton(onClick = { webView?.goBack() }, enabled = webView?.canGoBack() == true, modifier = Modifier.size(40.dp)) {
                Icon(Icons.Outlined.ArrowBack, "Назад")
            }
            OutlinedTextField(value = address, onValueChange = { address = it }, modifier = Modifier.weight(1f).height(54.dp), singleLine = true,
                label = { Text("Адрес HTTPS") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Go),
                keyboardActions = KeyboardActions(onGo = {
                    val target = if (address.startsWith("https://", true)) address else "https://$address"
                    if (Uri.parse(target).scheme == "https" && Uri.parse(target).host != null) webView?.loadUrl(target)
                    else vm.notify("Для безопасности поддерживаются только HTTPS-адреса")
                }))
            IconButton(onClick = { webView?.reload() }, modifier = Modifier.size(40.dp)) { Icon(Icons.Outlined.Refresh, "Обновить") }
            state.opened?.let { doc ->
                IconButton(onClick = {
                    clipboard.setText(AnnotatedString(doc.text))
                    vm.notify("Код скопирован. Вставьте его в Google вручную.")
                }, modifier = Modifier.size(40.dp)) { Icon(Icons.Outlined.ContentCopy, "Скопировать код") }
            }
        }
        if (pageLoading) LinearProgressIndicator(Modifier.fillMaxWidth())
        AndroidView(modifier = Modifier.weight(1f).fillMaxWidth(), factory = { ctx ->
            WebView(ctx).apply {
                webView = this
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                settings.allowFileAccess = false
                settings.allowContentAccess = false
                settings.allowFileAccessFromFileURLs = false
                settings.allowUniversalAccessFromFileURLs = false
                settings.javaScriptCanOpenWindowsAutomatically = false
                settings.setSupportMultipleWindows(false)
                settings.mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
                CookieManager.getInstance().setAcceptCookie(true)
                CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
                webChromeClient = WebChromeClient()
                webViewClient = object : WebViewClient() {
                    override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                        val uri = request.url
                        if (uri.scheme.equals("https", true) && !uri.host.isNullOrBlank()) return false
                        vm.notify("Заблокирован небезопасный или локальный адрес")
                        return true
                    }
                    override fun onPageStarted(view: WebView, url: String, favicon: android.graphics.Bitmap?) {
                        address = url; pageLoading = true
                    }
                    override fun onPageFinished(view: WebView, url: String) {
                        address = url; pageLoading = false
                    }
                    override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) {
                        if (request.isForMainFrame) { pageLoading = false; vm.notify("Страница не загрузилась: ${error.description}") }
                    }
                    override fun onReceivedSslError(view: WebView, handler: SslErrorHandler, error: SslError) {
                        pageLoading = false
                        handler.cancel()
                        vm.notify("Ошибка сертификата HTTPS. Переход отменён.")
                    }
                }
                loadUrl(GOOGLE_AI_MODE)
            }
        }, update = { view ->
            if (refreshKey > 0 && view.tag != refreshKey) { view.tag = refreshKey; view.reload() }
        })
    }
}
