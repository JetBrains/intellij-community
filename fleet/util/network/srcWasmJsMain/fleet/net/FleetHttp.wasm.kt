package fleet.net

import fleet.util.multiplatform.Actual
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.js.JsClient

@Actual
fun defaultHttpClientEngineWasmJs(trustManager: TrustManager, proxyConfiguration: HttpProxyConfiguration?): HttpClientEngine {
  return JsClient().create()
}