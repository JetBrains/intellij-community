package fleet.net

import fleet.util.logging.logger

data class HttpProxyConfiguration(
  /**
   * URL string with the following format `http://USER:PASS@HOST:PORT`
   */
  val httpProxyUrl: String?,
  /**
   * URL string with the following format `http://USER:PASS@HOST:PORT`
   *
   * Note: it should not mention `https` as protocol
   */
  val httpsProxyUrl: String?,
  /**
   * Comma-separated list of host for which not to respect proxy settings, follows `cURL` convention of `no_proxy` environment variable
   *
   * Example of values:
   *   - `*`
   *   - `localhost`
   *   - `localhost,.jetbrains.com`
   */
  val noProxy: String?,
) {
  private val isSet: Boolean
    get() = httpProxyUrl != null || httpsProxyUrl != null

  fun proxyDisabledFor(host: String): Boolean {
    if (noProxy == null) return false

    noProxy.split(",").forEach {
      if (it == "*") return true // cURL like behaviour
      if (host.endsWith(it.removePrefix("."))) return true  // cURL like behaviour
    }

    return false
  }

  companion object {
    private val logger = logger<HttpProxyConfiguration>()

    /**
     * Reads [HttpProxyConfiguration] from a serialized map representation given by printenv binary
     */
    fun readFromSettings(map: Map<String, String?>): HttpProxyConfiguration? {
      return HttpProxyConfiguration(
        httpProxyUrl = map["httpProxyUrl"]?.takeIf { it.isNotBlank() },
        httpsProxyUrl = map["httpsProxyUrl"]?.takeIf { it.isNotBlank() },
        noProxy = map["noProxy"]?.takeIf { it.isNotBlank() },
      ).also {
        logger.info { "Proxy from settings: $it" }
      }.takeIf { it.isSet }
    }

    /**
     *  Resolves proxy information from the host environment, keys considered are (other keys will be ignored):
     *  - `http_proxy` or `HTTP_PROXY` (first not null taken)
     *  - `https_proxy` or `HTTPS_PROXY` (first not null taken)
     *  - `no_proxy` or `NO_PROXY` (first not null taken)
     */
    fun readFromEnvironment(env: Map<String, String>): HttpProxyConfiguration? {
      return HttpProxyConfiguration(
        httpProxyUrl = (env["http_proxy"] ?: env["HTTP_PROXY"])?.takeIf { it.isNotBlank() },
        httpsProxyUrl = (env["https_proxy"] ?: env["HTTPS_PROXY"])?.takeIf { it.isNotBlank() },
        noProxy = env["no_proxy"] ?: env["NO_PROXY"],
      ).also {
        logger.debug { "Proxy from environment: $it" }
      }.takeIf { it.isSet }
    }
  }
}