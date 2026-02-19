package fleet.net

import fleet.util.Base64WithOptionalPadding
import fleet.util.logging.logger
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.HttpClientEngineCapability
import io.ktor.client.engine.HttpClientEngineConfig
import io.ktor.client.engine.HttpClientEngineFactory
import io.ktor.client.engine.ProxyBuilder
import io.ktor.client.engine.http
import io.ktor.client.plugins.api.createClientPlugin
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.http.HttpHeaders
import io.ktor.http.URLProtocol
import io.ktor.http.Url
import io.ktor.utils.io.InternalAPI
import kotlinx.coroutines.CoroutineDispatcher
import kotlin.coroutines.CoroutineContext
import kotlin.io.encoding.ExperimentalEncodingApi

/**
 * Factory that builds a delegating HttpClientEngine that supports proxy at request granularity (allowing `no_proxy`-like logic to work)
 *
 * The delegate factory will be used to create the engine(s).
 *
 * If [httpProxyConfiguration] is not set, only a delegate engine will be created and will always be used when requests are executed.
 *
 * If [httpProxyConfiguration] is set, one additional engine per proxy URL (one for [httpProxyConfiguration.httpProxyUrl] if set, one for [httpProxyConfiguration.httpsProxyUrl]` if set) will be created in addition of the proxy-less delegate engine.
 * Engine will be dynamically chosen everytime a request is executed.
 */
internal class ProxyDelegatingEngineFactory<out T : HttpClientEngineConfig>(
  private val factoryDelegate: HttpClientEngineFactory<T>,
  private val httpProxyConfiguration: HttpProxyConfiguration?,
) : HttpClientEngineFactory<T> {

  override fun create(block: T.() -> Unit): HttpClientEngine = ProxyDelegatingClientEngine(
    httpProxyConfiguration = httpProxyConfiguration,
    delegateEngine = factoryDelegate.create(block),
    httpProxyEngine = maybeCreateProxyEngine(httpProxyConfiguration?.httpProxyUrl, block),
    httpsProxyEngine = maybeCreateProxyEngine(httpProxyConfiguration?.httpsProxyUrl, block),
  )

  private fun maybeCreateProxyEngine(proxyUrl: String?, block: T.() -> Unit): HttpClientEngine? {
    if (proxyUrl == null) return null
    try {
      return factoryDelegate.create {
        block()
        proxy = ProxyBuilder.http(sanitizeUrl(proxyUrl))
      }
    } catch(e: Throwable) {
      reportInvalidProxySettings(e, proxyUrl)
      return null
    }
  }
}

/**
 * Kotlin can only parse urls with explicitly stated protocol
 * ProxyBuilder at the moment supports only http
 */
fun sanitizeUrl(url: String): String {
  return if (!url.contains("://")) {
    "http://$url"
  } else {
    url
  }
}

private fun reportInvalidProxySettings(e: Throwable, proxyUrl: String?) {
  when (e) {
    is IllegalStateException, is IllegalArgumentException -> {
      logger<HttpProxyConfiguration>().warn { "Can not parse URL config: $proxyUrl. Skipping proxy settings" }
    }
    else -> throw e
  }
}

internal class ProxyAuthenticationPluginConfig {
  var httpConfiguration: HttpProxyConfiguration? = null
}

internal val ProxyAuthenticationPlugin = createClientPlugin("ProxyAuthenticationPlugin", ::ProxyAuthenticationPluginConfig) {
  val proxyConfiguration = pluginConfig.httpConfiguration
  if (proxyConfiguration != null) {
  onRequest { request, _ ->
      val token = proxyBasicAuthenticationToken(request.url.protocol, proxyConfiguration)
      if (token != null) {
        request.headers.append(HttpHeaders.ProxyAuthorization, "Basic $token")
      }
    }
  }
}

private fun proxyBasicAuthenticationToken(protocol: URLProtocol, config: HttpProxyConfiguration?): String? {
  if (config == null) return null

  val proxyUrl = when (protocol) {
    URLProtocol.HTTP -> config.httpProxyUrl
    URLProtocol.HTTPS -> config.httpsProxyUrl
    else -> null
  }
  try {
    return proxyUrl
      ?.let { Url(sanitizeUrl(it)) }
      ?.base64BasicAuthentication()
  } catch(e: Throwable) {
    reportInvalidProxySettings(e, proxyUrl)
    return null
  }
}

@OptIn(ExperimentalEncodingApi::class)
private fun Url.base64BasicAuthentication(): String? = if (user != null && password != null) {
  Base64WithOptionalPadding.encode("${user}:${password}".encodeToByteArray())
} else {
  null
}

private class ProxyDelegatingClientEngine(
  val httpProxyConfiguration: HttpProxyConfiguration?,
  private val delegateEngine: HttpClientEngine,
  private val httpProxyEngine: HttpClientEngine?,
  private val httpsProxyEngine: HttpClientEngine?,
) : HttpClientEngine {
  override val config: HttpClientEngineConfig
    get() = delegateEngine.config
  override val coroutineContext: CoroutineContext
    get() = delegateEngine.coroutineContext
  override val dispatcher: CoroutineDispatcher
    get() = delegateEngine.dispatcher
  override val supportedCapabilities: Set<HttpClientEngineCapability<*>>
    get() = listOfNotNull(
      delegateEngine.supportedCapabilities,
      httpProxyEngine?.supportedCapabilities,
      httpsProxyEngine?.supportedCapabilities,
    ).reduce(Set<HttpClientEngineCapability<*>>::intersect)

  override fun close() {
    delegateEngine.close()
    httpProxyEngine?.close()
    httpsProxyEngine?.close()
  }

  @InternalAPI
  override suspend fun execute(data: HttpRequestData): HttpResponseData {
    val selectedEngine = selectEngine(data.url)
    logger.debug { "executing http request to host ${data.url.host} using engine with proxy settings: ${selectedEngine.config.proxy}" }
    return selectedEngine.execute(data)
  }

  private fun selectEngine(url: Url): HttpClientEngine {
    if (httpProxyConfiguration?.proxyDisabledFor(url.host) == true) return delegateEngine

    return when (url.protocol) {
      URLProtocol.HTTP -> httpProxyEngine ?: delegateEngine
      URLProtocol.HTTPS -> httpsProxyEngine ?: delegateEngine
      else -> delegateEngine
    }
  }

  companion object {
    private val logger = logger<ProxyDelegatingClientEngine>()
  }
}
