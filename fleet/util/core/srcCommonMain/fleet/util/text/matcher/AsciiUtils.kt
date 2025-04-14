// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package fleet.util.text.matcher

/**
 * String utility methods that assume that the string contents is ASCII-only (all codepoints are <= 127).
 * These methods may work faster but work incorrectly on other characters
 */
internal object AsciiUtils {
  /**
   * Implementation of [com.intellij.util.text.NameUtilCore.nextWord] for ASCII-only strings
   *
   * @param text text to find next word in
   * @param start starting position within the text
   * @return position of the next word; may point to the end of the string
   */
  fun nextWordAscii(text: String, start: Int): Int {
    if (!isLetterOrDigitAscii(text.get(start))) {
      return start + 1
    }

    var i = start
    while (i < text.length && isDigitAscii(text.get(i))) {
      i++
    }
    if (i > start) {
      // digits form a separate hump
      return i
    }

    while (i < text.length && isUpperAscii(text.get(i))) {
      i++
    }

    if (i > start + 1) {
      // several consecutive uppercase letter form a hump
      if (i == text.length || !isLetterAscii(text.get(i))) {
        return i
      }
      return i - 1
    }

    if (i == start) i += 1
    while (i < text.length && isLetterAscii(text.get(i)) && !isWordStartAscii(text, i)) {
      i++
    }
    return i
  }

  private fun isWordStartAscii(text: String, i: Int): Boolean {
    val cur = text.get(i)
    val prev = if (i > 0) text.get(i - 1) else 0.toChar()
    if (isUpperAscii(cur)) {
      if (isUpperAscii(prev)) {
        // check that we're not in the middle of an all-caps word
        val nextPos = i + 1
        if (nextPos >= text.length) return false
        return isLowerAscii(text.get(nextPos))
      }
      return true
    }
    if (isDigitAscii(cur)) {
      return true
    }
    if (!isLetterAscii(cur)) {
      return false
    }
    return i == 0 || !isLetterOrDigitAscii(text.get(i - 1))
  }

  private fun isLetterAscii(cur: Char): Boolean {
    return cur >= 'a' && cur <= 'z' || cur >= 'A' && cur <= 'Z'
  }

  private fun isLetterOrDigitAscii(cur: Char): Boolean {
    return isLetterAscii(cur) || isDigitAscii(cur)
  }

  private fun isDigitAscii(cur: Char): Boolean {
    return cur >= '0' && cur <= '9'
  }

  fun toUpperAscii(c: Char): Char {
    if (c >= 'a' && c <= 'z') {
      return (c.code + ('A'.code - 'a'.code)).toChar()
    }
    return c
  }

  fun toLowerAscii(c: Char): Char {
    if (c >= 'A' && c <= 'Z') {
      return (c.code - ('A'.code - 'a'.code)).toChar()
    }
    return c
  }

  fun isUpperAscii(c: Char): Boolean {
    return 'A' <= c && c <= 'Z'
  }

  fun isLowerAscii(c: Char): Boolean {
    return 'a' <= c && c <= 'z'
  }
}

fun String.isAscii(): Boolean = all { it.isAscii() }
fun Char.isAscii(): Boolean = this.code < 128

