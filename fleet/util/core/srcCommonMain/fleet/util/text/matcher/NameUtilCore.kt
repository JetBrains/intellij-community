// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package fleet.util.text.matcher

import de.cketti.codepoints.CodePoints
import de.cketti.codepoints.codePointAt
import fleet.util.text.Codepoint
import fleet.util.text.Direction
import fleet.util.text.codepoints

object NameUtilCore {
  private const val KANA_START = 0x3040
  private const val KANA_END = 0x3358
  private const val KANA2_START = 0xFF66
  private const val KANA2_END = 0xFF9D

  /**
   * Splits an identifier into words, separated with underscores or upper-case characters
   * (camel-case).
   *
   * @param name the identifier to split.
   * @return the array of strings into which the identifier has been split.
   */
  fun splitNameIntoWords(name: String): Array<String> {
    val underlineDelimited = name.split("_".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
    val result = mutableListOf<String>()
    for (word in underlineDelimited) {
      addAllWords(word, result)
    }
    return result.toTypedArray()
  }

  private fun addAllWords(text: String, result: MutableList<in String>) {
    var start = 0
    while (start < text.length) {
      val next = nextWord(text, start)
      result.add(text.substring(start, next))
      start = next
    }
  }

  fun nextWord(text: String, start: Int): Int {
    val ch = text.codePointAt(start)
    val chLen = CodePoints.charCount(ch)
    if (!ch.toChar().isLetterOrDigit()) {
      return start + chLen
    }

    var i = start
    while (i < text.length) {
      val codePoint = text.codePointAt(i)
      if (!codePoint.toChar().isDigit()) break
      i += CodePoints.charCount(codePoint)
    }
    if (i > start) {
      // digits form a separate hump
      return i
    }

    while (i < text.length) {
      val codePoint = text.codePointAt(i)
      if (!codePoint.toChar().isUpperCase()) break
      i += CodePoints.charCount(codePoint)
    }

    if (i > start + chLen) {
      // several consecutive uppercase letter form a hump
      if (i == text.length || !text.codePointAt(i).toChar().isLetter()) {
        return i
      }
      return i - CodePoints.charCount(text.codePointBefore(i).codepoint)
    }

    if (i == start) i += chLen
    while (i < text.length) {
      val codePoint = text.codePointAt(i)
      if (!codePoint.toChar().isLetter() || isWordStart(text, i)) break
      i += CodePoints.charCount(codePoint)
    }
    return i
  }

  fun isWordStart(text: String, i: Int): Boolean {
    val cur = text.codePointAt(i)
    val prev = if (i > 0) text.codePointBefore(i).codepoint else -1
    if (cur.toChar().isUpperCase()) {
      if (prev.toChar().isUpperCase()) {
        // check that we're not in the middle of an all-caps word
        val nextPos = i + CodePoints.charCount(cur)
        return nextPos < text.length && text.codePointAt(nextPos).toChar().isLowerCase()
      }
      return true
    }
    if (cur.toChar().isDigit()) {
      return true
    }
    if (!cur.toChar().isLetter()) {
      return false
    }
    if (isIdeographic(cur)) {
      // Consider every ideograph as a separate word
      return true
    }
    return i == 0 || !text[i - 1].toChar().isLetterOrDigit() || isHardCodedWordStart(text, i) ||
           isKanaBreak(cur, prev)
  }

  private fun maybeKana(codePoint: Int): Boolean {
    return codePoint >= KANA_START && codePoint <= KANA_END ||
           codePoint >= KANA2_START && codePoint <= KANA2_END
  }

  private fun isKanaBreak(cur: Int, prev: Int): Boolean {
    if (!maybeKana(cur) && !maybeKana(prev)) return false

    val curKatakana = isKatakana(cur)
    val prevKatakana = isKatakana(prev)
    val curHiragana = isHiragana(cur)
    val prevHiragana = isHiragana(prev)

    // was Character.UnicodeScript.of(cur) == Character.UnicodeScript.of(prev)
    // but if neither is katagana nor hiragana, this will return false afterward anyway
    if (curKatakana && prevKatakana || curHiragana && prevHiragana) return false
    return (curKatakana || curHiragana || prevKatakana || prevHiragana) && !isCommonScript(prev) && !isCommonScript(cur)
  }

  private fun isHardCodedWordStart(text: String, i: Int): Boolean {
    return text[i] == 'l' && i < text.length - 1 && text[i + 1] == 'n' &&
           (text.length == i + 2 || isWordStart(text, i + 2))
  }

  fun nameToWords(name: String): Array<String> {
    val array= mutableListOf<String>()
    var index = 0

    while (index < name.length) {
      val wordStart = index
      var upperCaseCount = 0
      var lowerCaseCount = 0
      var digitCount = 0
      var specialCount = 0
      while (index < name.length) {
        val c = name[index]
        if (c.toChar().isDigit()) {
          if (upperCaseCount > 0 || lowerCaseCount > 0 || specialCount > 0) break
          digitCount++
        }
        else if (c.toChar().isUpperCase()) {
          if (lowerCaseCount > 0 || digitCount > 0 || specialCount > 0) break
          upperCaseCount++
        }
        else if (c.toChar().isLowerCase()) {
          if (digitCount > 0 || specialCount > 0) break
          if (upperCaseCount > 1) {
            index--
            break
          }
          lowerCaseCount++
        }
        else {
          if (upperCaseCount > 0 || lowerCaseCount > 0 || digitCount > 0) break
          specialCount++
        }
        index++
      }
      val word = name.substring(wordStart, index)
      if (!word.isBlank()) {
        array.add(word)
      }
    }
    return array.toTypedArray()
  }
}


private fun String.codePointBefore(index: Int): Codepoint {
  return codepoints(index, direction = Direction.BACKWARD).next()
}