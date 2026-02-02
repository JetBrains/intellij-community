package fleet.net

import fleet.reporting.shared.tracing.span
import fleet.reporting.shared.tracing.spannedScope
import fleet.util.async.Resource
import fleet.util.async.async
import fleet.util.async.map
import fleet.util.async.onContext
import fleet.util.async.resource
import fleet.util.async.track
import fleet.util.async.use
import io.ktor.client.HttpClient
import io.ktor.client.HttpClientConfig
import io.ktor.client.engine.HttpClientEngine
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlin.coroutines.CoroutineContext

interface HttpClientApi {
  /**
   * Default HTTP engine for any HTTP operation in Fleet
   *
   * Avoid using it directly, prefer using [HttpClientApi.httpClient] instead.
   *
   * Careful: lifecycle of the engine must *NOT* be handled by the caller: DO NOT close this engine, e.g. do not use [use] nor [io.ktor.client.engine.HttpClientEngine.close] on it.
   */
  suspend fun httpClientEngine(): HttpClientEngine

  /**
   * Shared HTTP client that must be used for any HTTP request within Fleet
   *
   * Careful: lifecycle of the client must *NOT* be handled by the caller: DO NOT close this client, e.g. do not use [use] nor [HttpClient.close] on it.
   *
   * Need for custom configuration must be addressed by deriving from this client to avoid losing things such as proxy support or other defaults.
   * Note that derived client lifecycle must be handled by caller, hence the presence of `.use` in the following example.
   * E.g.
   * ```
   * val client = FleetHttp.httpClient.config {
   *   // your ktor client config overrides here
   * }.use { client ->
   *   // ...
   * }
   * ```
   */
  @Deprecated("prefer methods returning Resource")
  suspend fun httpClient(): HttpClient

  fun default(): Resource<HttpClient>

  fun custom(block: HttpClientConfig<*>.() -> Unit): Resource<HttpClient>

  class CoroutineContextElement(val httpClientApi: HttpClientApi) : CoroutineContext.Element {
    companion object : CoroutineContext.Key<CoroutineContextElement>

    override val key: CoroutineContext.Key<*> get() = CoroutineContextElement
  }
}

suspend fun requireHttpClient(): HttpClientApi =
  checkNotNull(currentCoroutineContext()[HttpClientApi.CoroutineContextElement]) {
    "must have an HttpClientApi in coroutine context"
  }.httpClientApi

fun httpClientApi(
  httpProxyConfigurationProvider: (suspend CoroutineScope.() -> HttpProxyConfiguration?)? = null,
  trustManagerProvider: suspend CoroutineScope.() -> TrustManager,
): Resource<HttpClientApi> =
  resource { cc ->
    spannedScope("httpClientApi") {
      val httpProxyConfiguration = httpProxyConfigurationProvider?.invoke(this)
      val trustManager = trustManagerProvider.invoke(this)
      defaultHttpClientEngine(trustManager, httpProxyConfiguration).use { httpClientEngine ->
        cc(httpClientEngine to httpProxyConfiguration)
      }
    }
  }
    .onContext(CoroutineName("httpClientApi"))
    .async(lazy = true)
    .track("httpClientApi")
    .map { httpClient ->
      object : HttpClientApi {
        override suspend fun httpClientEngine(): HttpClientEngine =
          httpClient.await().let { (engine, _) -> engine }

        override suspend fun httpClient(): HttpClient =
          httpClient.await().let { (engine, proxy) ->
            span(name = "defaultHttpClient") {
              defaultHttpClient(engine, proxy)
            }
          }

        override fun default(): Resource<HttpClient> = custom(block = {})

        override fun custom(block: HttpClientConfig<*>.() -> Unit): Resource<HttpClient> =
          resource { cc ->
            httpClient.await().let { (engine, proxy) ->
              span(name = "defaultHttpClient") { defaultHttpClient(engine, proxy).config(block) }.use {
                cc(it)
              }
            }
          }
      }
    }
