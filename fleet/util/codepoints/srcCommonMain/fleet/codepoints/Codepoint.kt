// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package fleet.codepoints

import de.cketti.codepoints.CodePoints
import kotlin.jvm.JvmInline

@JvmInline
value class Codepoint(val codepoint: Int) {
  val charCount: Int get() = CodePoints.charCount(codepoint)

  companion object {
    fun isUnicodeIdentifierStart(codepoint: Int): Boolean = isCodepointInRanges(codepoint, unicodeIdStartRanges)
    fun isUnicodeIdentifierPart(codepoint: Int): Boolean = isCodepointInRanges(codepoint, unicodeIdContinueRanges)
    fun isIdentifierIgnorable(codepoint: Int): Boolean = isCodepointInRanges(codepoint, identifierIgnorableRanges)
    fun isJavaIdentifierStart(codepoint: Int): Boolean = isCodepointInRanges(codepoint, javaIdStartRanges)
    fun isJavaIdentifierPart(codepoint: Int): Boolean = isCodepointInRanges(codepoint, javaIdPartRanges)
    fun isSpaceChar(codePoint: Int): Boolean = isCodepointInRanges(codePoint, spaceCharRanges)
    fun isWhitespace(codepoint: Int): Boolean = isCodepointInRanges(codepoint, whitespaceRanges)
    fun isIdeographic(codepoint: Int): Boolean = isCodepointInRanges(codepoint, ideographicRanges)
    fun isKatakana(codepoint: Int): Boolean = isCodepointInRanges(codepoint, katakanaRanges)
    fun isHiragana(codepoint: Int): Boolean = isCodepointInRanges(codepoint, hiraganaRanges)
    fun isCommonScript(codepoints: Int): Boolean = isCodepointInRanges(codepoints, commonRanges)

    fun isISOControl(codePoint: Int): Boolean {
      return codePoint in 0x00..0x1F || // 0000..001F    ; Common # Cc  [32] <control-0000>..<control-001F>
             codePoint in 0x7F..0x9F // 007F..009F    ; Common # Cc  [33] <control-007F>..<control-009F>
    }

    fun isUpperCase(codepoint: Int): Boolean {
      // fast path
      if (codepoint in 'A'.code..'Z'.code) {
        return true
      }
      if (codepoint < '\u0080'.code) {
        return false
      }
      // proper check
      return isCodepointInRanges(codepoint, uppercaseRanges)
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

enum class Direction {
  FORWARD,
  BACKWARD,
}