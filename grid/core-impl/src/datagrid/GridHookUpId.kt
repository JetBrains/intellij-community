package com.intellij.database.datagrid

import org.jetbrains.annotations.ApiStatus
import kotlin.random.Random

/**
 * Names a hookup that [GridHookUpRegistry] registered, so an editor or a diff can acquire it later.
 * It stays valid while the hookup is registered, also while the registry keeps it for a reopen.
 * Its string form is 8 random characters of lowercase Crockford base32 that start with a letter, such as `k7m2q9xd`.
 * The alphabet has no case, no `i`, `l`, `o` or `u`, and no `-`, so a model copies it reliably.
 * A command line doesn't read it as a flag.
 * The ids of one registry don't follow each other, so a miscopied id names another hookup only by rare chance.
 * Clients outside the registry, such as AI tools, treat the string as opaque.
 */
@ApiStatus.Internal
@ConsistentCopyVisibility
data class GridHookUpId internal constructor(private val value: Long) {
  override fun toString(): String = buildString(LENGTH) {
    for (index in LENGTH - 1 downTo 0) {
      append(ALPHABET[(value ushr index * BITS).toInt() and DIGIT_MASK])
    }
  }

  companion object {
    private const val ALPHABET = "0123456789abcdefghjkmnpqrstvwxyz"
    private const val LENGTH = 8
    private const val BITS = 5
    private const val DIGIT_MASK = (1 shl BITS) - 1

    /** The first character is a letter, so an id never reads as a number, such as `12345678` or `1e100000`. */
    private const val SMALLEST = 10L shl (LENGTH - 1) * BITS
    private const val LIMIT = 1L shl LENGTH * BITS

    /** Returns the id of [value], or null unless [value] is the exact text of an id, as [toString] gives it. */
    fun parseOrNull(value: String): GridHookUpId? {
      if (value.length != LENGTH) return null
      var parsed = 0L
      for (char in value) {
        val digit = ALPHABET.indexOf(char)
        if (digit < 0) return null
        parsed = (parsed shl BITS) or digit.toLong()
      }
      return if (parsed >= SMALLEST) GridHookUpId(parsed) else null
    }

    /** An id is no secret, so it needs no `SecureRandom`. */
    internal fun newRandom(): GridHookUpId = GridHookUpId(Random.nextLong(SMALLEST, LIMIT))
  }
}
