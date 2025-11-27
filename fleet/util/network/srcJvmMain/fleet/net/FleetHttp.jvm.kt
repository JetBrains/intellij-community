package fleet.net

import fleet.util.multiplatform.Actual
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.cio.CIO
import java.security.cert.X509Certificate
import javax.net.ssl.X509TrustManager

@Actual
internal fun defaultHttpClientEngineJvm(trustManager: TrustManager, proxyConfiguration: HttpProxyConfiguration?): HttpClientEngine {
  FleetHttp.logger.debug { "creating default HttpClientEngine" }
  return ProxyDelegatingEngineFactory(CIO, proxyConfiguration).create {
    https {
      this.trustManager = trustManager as javax.net.ssl.TrustManager
    }
  }
}

object NoopTrustManager : X509TrustManager {
  override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) {
  }

  override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) {
  }

  override fun getAcceptedIssuers(): Array<X509Certificate> {
    return emptyArray()
  }
}