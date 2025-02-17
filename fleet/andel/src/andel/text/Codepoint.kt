// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package andel.text

import kotlin.jvm.JvmInline
import de.cketti.codepoints.CodePoints

@JvmInline
value class Codepoint(val codepoint: Int) {
  val charCount: Int get() = CodePoints.charCount(codepoint)

  companion object {
    fun isUnicodeIdentifierPart(codepoint: Int): Boolean {
      return isTrueBasedOnRange(codepoint, eitherUnicodeOrJavaStartIdRanges) || isTrueBasedOnRange(codepoint, unicodeIdRanges)
    }

    fun isJavaIdentifierPart(codepoint: Int): Boolean {
      return isTrueBasedOnRange(codepoint, eitherUnicodeOrJavaStartIdRanges) || isTrueBasedOnRange(codepoint, javaStartIdRanges)
    }

    fun isSpaceChar(codePoint: Int): Boolean {
      // Could be substituted by Char.isWhitespace() && !char.isISOControl() && /* + extra filters for java's !Character.isWhitespace(char) */
      return when (codePoint) {
        0x20, 0xA0, 0x1680, 0x202F, 0x205F, 0x3000 -> true
        in 0x2000..<0x200B -> true
        in 0x2028..<0x202A -> true
        else -> false
      }
    }

    /**
     * @see Character.isISOControl
     */
    fun isISOControl(codePoint: Int): Boolean {
      // copy of: Character.isISOControl(codePoint)
      // Optimized form of:
      //     (codePoint >= 0x00 && codePoint <= 0x1F) ||
      //     (codePoint >= 0x7F && codePoint <= 0x9F);
      return codePoint <= 0x9F &&
             (codePoint >= 0x7F || codePoint.shr(5) == 0)
    }

    fun toString(codepoint: Int): String {
      return CodePoints.toString(codepoint)
    }
  }
}

fun CharSequence.codepoints(offset: Int, direction: Direction = Direction.FORWARD): Iterator<Codepoint> =
  when (direction) {
    Direction.FORWARD -> iterator {
      var i = offset
      val len = length
      while (i < len) {
        val c1 = get(i++)
        if (c1.isHighSurrogate()) {
          if (i < len) {
            val c2 = get(i++)
            if (c2.isLowSurrogate()) {
              yield(Codepoint(CodePoints.toCodePoint(c1, c2)))
            }
          }
        }
        else {
          yield(Codepoint(c1.code))
        }
      }
    }
    Direction.BACKWARD -> iterator {
      var i = offset - 1
      while (i >= 0) {
        val c2 = get(i--)
        if (c2.isLowSurrogate()) {
          if (i >= 0) {
            val c1 = get(i--)
            if (c1.isHighSurrogate()) {
              yield(Codepoint(CodePoints.toCodePoint(c1, c2)))
            }
          }
        }
        else {
          yield(Codepoint(c2.code))
        }
      }
    }
  }
