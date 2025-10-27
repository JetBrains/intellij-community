package fleet.net

import fleet.reporting.shared.tracing.span
import fleet.util.Base64WithOptionalPadding
import fleet.util.UrlSafeBase64WithOptionalPadding
import java.security.SecureRandom
import kotlin.io.encoding.ExperimentalEncodingApi

const val SECRET_SIZE: Int = 256

// lives here because it is accessed from dock impl
@OptIn(ExperimentalEncodingApi::class)
fun generateSecret(): String {
  return span("generateSecret") {
    val random = SecureRandom()
    val bytes = ByteArray(SECRET_SIZE)
    random.nextBytes(bytes)
    UrlSafeBase64WithOptionalPadding.encode(bytes)
  }
}

fun isValidSecret(value: String): Boolean =
  try {
    UrlSafeBase64WithOptionalPadding.decode(value).size == SECRET_SIZE
  }
  catch (_: RuntimeException) {
    false
  }
