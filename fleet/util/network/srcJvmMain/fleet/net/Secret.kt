package fleet.net

import fleet.reporting.shared.tracing.span
import fleet.util.UrlSafeBase64WithOptionalPadding
import java.security.SecureRandom
import kotlin.io.encoding.ExperimentalEncodingApi

// lives here because it is accessed from dock impl
@OptIn(ExperimentalEncodingApi::class)
fun generateSecret(): String {
  return span("generateSecret") {
    val random = SecureRandom()
    val bytes = ByteArray(256)
    random.nextBytes(bytes)
    UrlSafeBase64WithOptionalPadding.encode(bytes)
  }
}
