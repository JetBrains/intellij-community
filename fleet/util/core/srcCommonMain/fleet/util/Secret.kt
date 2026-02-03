// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package fleet.util

import fleet.reporting.shared.tracing.span
import kotlin.random.Random

private const val SECRET_SIZE: Int = 256

// lives here because it is accessed from dock impl
class Secret(private val secret: ByteArray) {

  init {
    require(secret.size == SECRET_SIZE) {
      "Secret should of size ${SECRET_SIZE}. ${secret.size} given"
    }
  }

  companion object {
    fun generate(): Secret {
      return span("generateSecret") {
        val bytes = ByteArray(SECRET_SIZE)
        Random.nextBytes(bytes)
        Secret(bytes)
      }
    }

    fun readFromString(secretAsString: String): Secret? {
      return try {
        UrlSafeBase64WithOptionalPadding.decode(secretAsString)
          .takeIf { it.size == SECRET_SIZE }
          ?.let { Secret(it) }
      }
      catch (_: IllegalArgumentException) {
        null
      }
    }
  }

  fun toBase64String(): String {
    return UrlSafeBase64WithOptionalPadding.encode(secret)
  }

  override fun toString(): String {
    // the value of secret is intentionally ommited to prevent accidental leak
    return "Secret<***>"
  }
}