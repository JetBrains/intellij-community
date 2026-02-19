package fleet.net

import fleet.util.multiplatform.Actual
import io.ktor.client.*
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.darwin.*

@Actual
fun defaultHttpClientEngineNative(trustManager: fleet.net.TrustManager, proxyConfiguration: fleet.net.HttpProxyConfiguration?): HttpClientEngine {
  return HttpClient(Darwin).engine
}