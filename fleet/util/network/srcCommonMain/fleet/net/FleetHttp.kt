package fleet.net

import fleet.util.logging.logger
import fleet.util.multiplatform.linkToActual
import io.ktor.client.HttpClient
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.plugins.DefaultRequest
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.header
import kotlin.time.Duration.Companion.seconds

internal object FleetHttp {
  val logger = logger<FleetHttp>()
}

/**
 * Should not be used directly, prefer [HttpClientApi.httpClientEngine] using key [HttpClientApi] on your [coroutineContext]
 */
//@fleet.kernel.plugins.InternalInPluginModules(where = ["fleet.testlib"])
fun defaultHttpClientEngine(trustManager: TrustManager, proxyConfiguration: HttpProxyConfiguration?): HttpClientEngine = linkToActual()

typealias TrustManager = Any // this can be fixed by introducing expect/actual class, but we don't support it in out compiler plugin yet

/**
 * Should not be used directly, prefer [HttpClientApi.httpClient] using key [HttpClientApi] on your [coroutineContext]
 */
//@fleet.kernel.plugins.InternalInPluginModules(where = ["fleet.space.tests"])
fun defaultHttpClient(engine: HttpClientEngine, proxyConfiguration: HttpProxyConfiguration?): HttpClient {
  FleetHttp.logger.debug { "creating default HttpClient" }
  return HttpClient(engine) {
    install(DefaultRequest) {
      header("User-Agent", "Fleet/1.0")
    }
    install(ProxyAuthenticationPlugin) {
      httpConfiguration = proxyConfiguration
    }

    expectSuccess = true
    followRedirects = true

    install(HttpTimeout) {
      connectTimeoutMillis = 30.seconds.inWholeMilliseconds
    }
  }
}