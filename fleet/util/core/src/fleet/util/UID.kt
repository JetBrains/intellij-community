// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package fleet.util

import fleet.preferences.isFleetInternalDefaultValue
import fleet.util.logging.logger
import fleet.util.serialization.DelegateSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.serializer

/**
 * UID is a random [A-Za-z0-9_-] string, case-sensitive, no more than 36 characters long
 */
@Serializable(with = UID.Serializer::class)
class UID private constructor(val id: String) {
  init {
    if (id.length > MAX_LENGTH) {
      logger.warn(Throwable()) {
        "Invalid UID format: \"$id\", UID is a random [A-Za-z0-9_-] string, case-sensitive, no more than 36 characters long. This will be a hard error in the future."
      }
    }
  }

  object Serializer : DelegateSerializer<UID, String>(UID::toString, String.serializer(), ::UID)

  companion object {
    private val logger = logger<UID>()

    private val uidRegex = Regex("^([A-Za-z0-9_-]{1,36})$")
    const val MAX_LENGTH = 36

    private const val LEN = 20

    fun random(): UID = with(Random) {
      UID(nextUidString(LEN))
    }

    fun fromString(id: String): UID = run {
      if (isFleetInternalDefaultValue && !id.matches(uidRegex)) {
        logger.error(Throwable()) {
          "Invalid UID format: \"$id\", UID is a random [A-Za-z0-9_-] string, case-sensitive, no more than 36 characters long. This will be a hard error in the future."
        }
      }
      UID(id)
    }
  }

  override fun equals(other: Any?): Boolean {
    return this === other || (other is UID && id == other.id)
  }

  override fun hashCode(): Int {
    return id.hashCode()
  }

  override fun toString(): String = id
}

typealias UIDSerializer = UID.Serializer
