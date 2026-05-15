// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package fleet.util

import fleet.util.serialization.DelegateSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.serializer
import kotlin.random.Random

/**
 * UID is a random [A-Za-z0-9_-] string, case-sensitive, no more than 36 characters long
 */
@Serializable(with = UID.Serializer::class)
class UID private constructor(val id: String) {
  object Serializer : DelegateSerializer<UID, String>(UID::toString, String.serializer(), UID::fromString)

  companion object {
    private const val MAX_LENGTH: Int = 36
    private val uidRegex = Regex("^([A-Za-z0-9_-]{1,$MAX_LENGTH})$")

    fun random(): UID {
      val string = buildString {
        repeat(3) {
          // Would give 8 chars per chunk (radix 32 -> 5 bits per char, 5 * 8 bits generated)
          append(Random.nextBytes(5).toLong().toString(32).padStart(8, '0'))
        }
      }

      return UID(string.take(20))
    }

    fun isUid(id: String): Boolean = id.matches(uidRegex)

    fun fromString(id: String): UID = run {
      check(isUid(id)) {
        "Invalid UID format: \"$id\", UID is a random [A-Za-z0-9_-] string, case-sensitive, no more than 36 characters long."
      }
      UID(id)
    }

    private fun ByteArray.toLong(): Long {
      var number = 0L
      for ((index, i) in (size - 1 downTo 0).withIndex()) { // big-endian
        val bitIndex = i * 8
        number = get(index).toLong() and 0xff shl bitIndex or number
      }
      return number
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
