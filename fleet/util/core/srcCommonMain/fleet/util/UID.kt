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
    private const val LENGTH: Int = 20
    // *5 – LENGTH chunks 5 bits each (len*5)
    // /8 – split by bytes
    // +7 – ceiling the value: (x + n - 1) / n ≡ ceil(x / n)
    private const val BYTES_LENGTH: Int = (LENGTH * 5 + 7) / 8
    private val BASE32 = "0123456789abcdefghijklmnopqrstuv".toCharArray()

    private const val MAX_LENGTH: Int = 36
    private val uidRegex = Regex("^([A-Za-z0-9_-]{1,$MAX_LENGTH})$")

    fun random(): UID {
      val bytes = Random.nextBytes(BYTES_LENGTH)
      val chars = CharArray(LENGTH)
      var bitBuf = 0
      var bitCount = 0
      var byteIdx = 0
      for (i in 0 until LENGTH) {
        if (bitCount < 5) {
          bitBuf = (bitBuf shl 8) or (bytes[byteIdx++].toInt() and 0xff)
          bitCount += 8
        }
        bitCount -= 5
        chars[i] = BASE32[(bitBuf ushr bitCount) and 31]
      }
      return UID(chars.concatToString())
    }

    fun isUid(id: String): Boolean = id.matches(uidRegex)

    fun fromString(id: String): UID = run {
      check(isUid(id)) {
        "Invalid UID format: \"$id\", UID is a random [A-Za-z0-9_-] string, case-sensitive, no more than 36 characters long."
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
