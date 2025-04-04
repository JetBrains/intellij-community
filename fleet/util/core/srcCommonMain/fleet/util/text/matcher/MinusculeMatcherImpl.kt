// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package fleet.util.text.matcher

import kotlinx.collections.immutable.PersistentList
import kotlinx.collections.immutable.persistentListOf

/**
 * Tells whether a string matches a specific pattern. Allows for lowercase camel-hump matching.
 * Used in navigation, code completion, speed search etc.
 *
 * @see NameUtil.buildMatcher
 */
internal class MinusculeMatcherImpl(
  pattern: String,
  private val myOptions: NameUtil.MatchingCaseSensitivity,
  private val myHardSeparators: String,
) : MinusculeMatcher() {
  private val myPattern = pattern.removeSuffix("* ").toCharArray()
  private val myHasHumps: Boolean
  private val myHasSeparators: Boolean
  private val myHasDots: Boolean
  private val isLowerCase = BooleanArray(myPattern.size)
  private val isUpperCase = BooleanArray(myPattern.size)
  private val isWordSeparator = BooleanArray(myPattern.size)
  private val toUpperCase = CharArray(myPattern.size)
  private val toLowerCase = CharArray(myPattern.size)
  private val myMeaningfulCharacters: CharArray
  private val myMinNameLength: Int

  /**
   * Constructs a matcher by a given pattern.
   * @param pattern the pattern
   * @param myOptions case sensitivity settings
   * @param myHardSeparators A string of characters (empty by default). Lowercase humps don't work for parts separated by any of these characters.
   * Need either an explicit uppercase letter or the same separator character in prefix
   */
  init {
    val meaningful = StringBuilder()
    for (k in myPattern.indices) {
      val c = myPattern[k]
      isLowerCase[k] = c.toChar().isLowerCase()
      isUpperCase[k] = c.toChar().isUpperCase()
      isWordSeparator[k] = isWordSeparator(c)
      toUpperCase[k] = c.uppercaseChar()
      toLowerCase[k] = c.lowercaseChar()
      if (!isWildcard(k)) {
        meaningful.append(toLowerCase[k])
        meaningful.append(toUpperCase[k])
      }
    }
    var i = 0
    while (isWildcard(i)) i++
    myHasHumps = hasFlag(i + 1, isUpperCase) && hasFlag(i, isLowerCase)
    myHasSeparators = hasFlag(i, isWordSeparator)
    myHasDots = hasDots(i)
    myMeaningfulCharacters = meaningful.toString().toCharArray()
    myMinNameLength = myMeaningfulCharacters.size / 2
  }

  private fun hasFlag(start: Int, flags: BooleanArray): Boolean {
    for (i in start..<myPattern.size) {
      if (flags[i]) {
        return true
      }
    }
    return false
  }

  private fun hasDots(start: Int): Boolean {
    for (i in start..<myPattern.size) {
      if (myPattern[i] == '.') {
        return true
      }
    }
    return false
  }

  override fun matchingDegree(
    name: String,
    valueStartCaseMatch: Boolean,
    fragments: PersistentList<TextRange>?,
  ): Int {
    if (fragments == null) return Int.Companion.MIN_VALUE
    if (fragments.isEmpty()) return 0

    val first = fragments.first()
    val startMatch = first.startOffset == 0
    val valuedStartMatch = startMatch && valueStartCaseMatch

    var matchingCase = 0
    var p = -1

    var skippedHumps = 0
    var nextHumpStart = 0
    var humpStartMatchedUpperCase = false
    for (range in fragments) {
      for (i in range.startOffset..<range.endOffset) {
        val afterGap = i == range.startOffset && first !== range
        var isHumpStart = false
        while (nextHumpStart <= i) {
          if (nextHumpStart == i) {
            isHumpStart = true
          }
          else if (afterGap) {
            skippedHumps++
          }
          nextHumpStart = nextWord(name, nextHumpStart)
        }

        val c = name[i]
        p = myPattern.indexOf(c, startIndex = p + 1, endIndex = myPattern.size, ignoreCase = false)
        if (p < 0) {
          break
        }

        if (isHumpStart) {
          humpStartMatchedUpperCase = c == myPattern[p] && isUpperCase[p]
        }

        matchingCase += evaluateCaseMatching(valuedStartMatch, p, humpStartMatchedUpperCase, i, afterGap, isHumpStart, c)
      }
    }

    val startIndex: Int = first.startOffset
    val afterSeparator = name.indexOfAny(myHardSeparators, 0, startIndex) >= 0
    val wordStart = startIndex == 0 || NameUtilCore.isWordStart(name, startIndex) && !NameUtilCore.isWordStart(name, startIndex - 1)
    val finalMatch = fragments[fragments.size - 1].endOffset == name.length

    return (if (wordStart) 1000 else 0) +
           matchingCase -
           fragments.size + -skippedHumps * 10 +
           (if (afterSeparator) 0 else 2) +
           (if (startMatch) 1 else 0) +
           (if (finalMatch) 1 else 0)
  }

  private fun evaluateCaseMatching(
    valuedStartMatch: Boolean,
    patternIndex: Int,
    humpStartMatchedUpperCase: Boolean,
    nameIndex: Int,
    afterGap: Boolean,
    isHumpStart: Boolean,
    nameChar: Char,
  ): Int {
    if (afterGap && isHumpStart && isLowerCase[patternIndex]) {
      return -10 // disprefer when there's a hump but nothing in the pattern indicates the user meant it to be hump
    }
    if (nameChar == myPattern[patternIndex]) {
      if (isUpperCase[patternIndex]) return 50 // strongly prefer user's uppercase matching uppercase: they made an effort to press Shift

      if (nameIndex == 0 && valuedStartMatch) return 150 // the very first letter case distinguishes classes in Java etc

      if (isHumpStart) return 1 // if a lowercase matches lowercase hump start, that also means something
    }
    else if (isHumpStart) {
      // disfavor hump starts where pattern letter case doesn't match name case
      return -1
    }
    else if (isLowerCase[patternIndex] && humpStartMatchedUpperCase) {
      // disfavor lowercase non-humps matching uppercase in the name
      return -1
    }
    return 0
  }

  override val pattern: String
    get() = myPattern.concatToString()

  override fun matchingFragments(name: String): PersistentList<TextRange>? {
    if (name.length < myMinNameLength) {
      return null
    }

    if (myPattern.size > MAX_CAMEL_HUMP_MATCHING_LENGTH) {
      return matchBySubstring(name)
    }

    val length = name.length
    var patternIndex = 0
    var i = 0
    while (i < length && patternIndex < myMeaningfulCharacters.size) {
      val c = name[i]
      if (c == myMeaningfulCharacters[patternIndex] || c == myMeaningfulCharacters[patternIndex + 1]) {
        patternIndex += 2
      }
      ++i
    }
    if (patternIndex < myMinNameLength * 2) {
      return null
    }
    val isAscii: Boolean = name.isAscii()
    return matchWildcards(name, 0, 0, isAscii)
  }

  private fun matchBySubstring(name: String): PersistentList<TextRange>? {
    val infix = isPatternChar(0, '*')
    val patternWithoutWildChar = filterWildcard(myPattern)
    if (name.length < patternWithoutWildChar.size) {
      return null
    }
    if (infix) {
      val index: Int = name.indexOfIgnoreCase(CharArrayCharSequence(patternWithoutWildChar, 0, patternWithoutWildChar.size))
      if (index >= 0) {
        return persistentListOf(TextRange.from(index, patternWithoutWildChar.size - 1))
      }
      return null
    }

    if (patternWithoutWildChar.regionMatches(0, patternWithoutWildChar.size, name)) {
      return persistentListOf(TextRange(0, patternWithoutWildChar.size))
    }
    return null
  }

  /**
   * After a wildcard (* or space), search for the first non-wildcard pattern character in the name starting from nameIndex
   * and try to [.matchFragment] for it.
   */
  private fun matchWildcards(
    name: String,
    patternIndex: Int,
    nameIndex: Int,
    isAsciiName: Boolean,
  ): PersistentList<TextRange>? {
    var patternIndex = patternIndex
    if (nameIndex < 0) {
      return null
    }
    if (!isWildcard(patternIndex)) {
      if (patternIndex == myPattern.size) {
        return persistentListOf()
      }
      return matchFragment(name, patternIndex, nameIndex, isAsciiName)
    }

    do {
      patternIndex++
    }
    while (isWildcard(patternIndex))

    if (patternIndex == myPattern.size) {
      // the trailing space should match if the pattern ends with the last word part, or only its first hump character
      if (this.isTrailingSpacePattern && nameIndex != name.length && (patternIndex < 2 || !isUpperCaseOrDigit(
          myPattern[patternIndex - 2]))
      ) {
        val spaceIndex = name.indexOf(' ', nameIndex)
        if (spaceIndex >= 0) {
          return persistentListOf(TextRange.from(spaceIndex, 1))
        }
        return null
      }
      return persistentListOf()
    }

    return matchSkippingWords(name, patternIndex,
                              findNextPatternCharOccurrence(name, nameIndex, patternIndex, isAsciiName),
                              true, isAsciiName)
  }

  private val isTrailingSpacePattern: Boolean
    get() = isPatternChar(myPattern.size - 1, ' ')

  /**
   * Enumerates places in name that could be matched by the pattern at patternIndex position
   * and invokes [.matchFragment] at those candidate positions
   */
  private fun matchSkippingWords(
    name: String,
    patternIndex: Int,
    nameIndex: Int,
    allowSpecialChars: Boolean,
    isAsciiName: Boolean,
  ): PersistentList<TextRange>? {
    var nameIndex = nameIndex
    var maxFoundLength = 0
    while (nameIndex >= 0) {
      val fragmentLength = if (seemsLikeFragmentStart(name, patternIndex, nameIndex)) maxMatchingFragment(name, patternIndex,
                                                                                                          nameIndex)
      else 0

      // match the remaining pattern only if we haven't already seen fragment of the same (or bigger) length
      // because otherwise it means that we already tried to match remaining pattern letters after it with the remaining name and failed
      // but now we have the same remaining pattern letters and even less remaining name letters, and so will fail as well
      if (fragmentLength > maxFoundLength || nameIndex + fragmentLength == name.length && this.isTrailingSpacePattern) {
        if (!isMiddleMatch(name, patternIndex, nameIndex)) {
          maxFoundLength = fragmentLength
        }
        val ranges = matchInsideFragment(name, patternIndex, nameIndex, isAsciiName, fragmentLength)
        if (ranges != null) {
          return ranges
        }
      }
      val next = findNextPatternCharOccurrence(name, nameIndex + 1, patternIndex, isAsciiName)
      nameIndex = if (allowSpecialChars) next else checkForSpecialChars(name, nameIndex + 1, next, patternIndex)
    }
    return null
  }

  private fun findNextPatternCharOccurrence(
    name: String,
    startAt: Int,
    patternIndex: Int,
    isAsciiName: Boolean,
  ): Int {
    return if (!isPatternChar(patternIndex - 1, '*') && !isWordSeparator[patternIndex])
      indexOfWordStart(name, patternIndex, startAt, isAsciiName)
    else
      indexOfIgnoreCase(name, startAt, myPattern[patternIndex], patternIndex, isAsciiName)
  }

  private fun checkForSpecialChars(name: String, start: Int, end: Int, patternIndex: Int): Int {
    if (end < 0) return -1

    // pattern humps are allowed to match in words separated by " ()", lowercase characters aren't
    if (!myHasSeparators && !myHasHumps && name.indexOfAny(myHardSeparators, start, end) >= 0) {
      return -1
    }
    // if the user has typed a dot, don't skip other dots between humps
    // but one pattern dot may match several name dots
    if (myHasDots && !isPatternChar(patternIndex - 1, '.') && name.indexOf('.', start, end) >= 0) {
      return -1
    }
    return end
  }

  private fun seemsLikeFragmentStart(name: String, patternIndex: Int, nextOccurrence: Int): Boolean {
    // uppercase should match either uppercase or a word start
    return !isUpperCase[patternIndex] ||
           name[nextOccurrence].toChar().isUpperCase() ||
           NameUtilCore.isWordStart(name,
                                    nextOccurrence) ||  // accept uppercase matching lowercase if the whole prefix is uppercase and case sensitivity allows that
           !myHasHumps && myOptions != NameUtil.MatchingCaseSensitivity.ALL
  }

  private fun charEquals(patternChar: Char, patternIndex: Int, c: Char, isIgnoreCase: Boolean): Boolean {
    return patternChar == c ||
           isIgnoreCase && (toLowerCase[patternIndex] == c || toUpperCase[patternIndex] == c)
  }

  private fun matchFragment(
    name: String,
    patternIndex: Int,
    nameIndex: Int,
    isAsciiName: Boolean,
  ): PersistentList<TextRange>? {
    val fragmentLength = maxMatchingFragment(name, patternIndex, nameIndex)
    return if (fragmentLength == 0) null else matchInsideFragment(name, patternIndex, nameIndex, isAsciiName, fragmentLength)
  }

  private fun maxMatchingFragment(name: String, patternIndex: Int, nameIndex: Int): Int {
    if (!isFirstCharMatching(name, nameIndex, patternIndex)) {
      return 0
    }

    var i = 1
    val ignoreCase = myOptions != NameUtil.MatchingCaseSensitivity.ALL
    while (nameIndex + i < name.length && patternIndex + i < myPattern.size) {
      val nameChar = name[nameIndex + i]
      if (!charEquals(myPattern[patternIndex + i], patternIndex + i, nameChar, ignoreCase)) {
        if (isSkippingDigitBetweenPatternDigits(patternIndex + i, nameChar)) {
          return 0
        }
        break
      }
      i++
    }
    return i
  }

  private fun isSkippingDigitBetweenPatternDigits(patternIndex: Int, nameChar: Char): Boolean {
    return myPattern[patternIndex].toChar().isDigit() && myPattern[patternIndex - 1].toChar().isDigit() && nameChar.toChar().isDigit()
  }

  // we've found the longest fragment matching pattern and name
  private fun matchInsideFragment(
    name: String,
    patternIndex: Int,
    nameIndex: Int,
    isAsciiName: Boolean,
    fragmentLength: Int,
  ): PersistentList<TextRange>? {
    // exact middle matches have to be at least of length 3, to prevent too many irrelevant matches
    val minFragment = if (isMiddleMatch(name, patternIndex, nameIndex))
      3
    else
      1

    val camelHumpRanges = improveCamelHumps(name, patternIndex, nameIndex, isAsciiName, fragmentLength, minFragment)
    if (camelHumpRanges != null) {
      return camelHumpRanges
    }

    return findLongestMatchingPrefix(name, patternIndex, nameIndex, isAsciiName, fragmentLength, minFragment)
  }

  private fun isMiddleMatch(name: String, patternIndex: Int, nameIndex: Int): Boolean {
    return isPatternChar(patternIndex - 1, '*') && !isWildcard(patternIndex + 1) &&
           name[nameIndex].toChar().isLetterOrDigit() && !NameUtilCore.isWordStart(name, nameIndex)
  }

  private fun findLongestMatchingPrefix(
    name: String,
    patternIndex: Int,
    nameIndex: Int,
    isAsciiName: Boolean,
    fragmentLength: Int, minFragment: Int,
  ): PersistentList<TextRange>? {
    if (patternIndex + fragmentLength >= myPattern.size) {
      return persistentListOf(TextRange.from(nameIndex, fragmentLength))
    }

    // try to match the remainder of pattern with the remainder of name
    // it may not succeed with the longest matching fragment, then try shorter matches
    var i = fragmentLength
    while (i >= minFragment || (i > 0 && isWildcard(patternIndex + i))) {
      val ranges: PersistentList<TextRange>?
      if (isWildcard(patternIndex + i)) {
        ranges = matchWildcards(name, patternIndex + i, nameIndex + i, isAsciiName)
      }
      else {
        var nextOccurrence = findNextPatternCharOccurrence(name, nameIndex + i + 1, patternIndex + i, isAsciiName)
        nextOccurrence = checkForSpecialChars(name, nameIndex + i, nextOccurrence, patternIndex + i)
        if (nextOccurrence >= 0) {
          ranges = matchSkippingWords(name, patternIndex + i, nextOccurrence, false, isAsciiName)
        }
        else {
          ranges = null
        }
      }
      if (ranges != null) {
        return prependRange(ranges, nameIndex, i)
      }
      i--
    }
    return null
  }

  /**
   * When pattern is "CU" and the name is "CurrentUser", we already have a prefix "Cu" that matches,
   * but we try to find uppercase "U" later in name for better matching degree
   */
  private fun improveCamelHumps(
    name: String,
    patternIndex: Int,
    nameIndex: Int,
    isAsciiName: Boolean,
    maxFragment: Int,
    minFragment: Int,
  ): PersistentList<TextRange>? {
    for (i in minFragment..<maxFragment) {
      if (isUppercasePatternVsLowercaseNameChar(name, patternIndex + i, nameIndex + i)) {
        val ranges = findUppercaseMatchFurther(name, patternIndex + i, nameIndex + i, isAsciiName)
        if (ranges != null) {
          return prependRange(ranges, nameIndex, i)
        }
      }
    }
    return null
  }

  private fun isUppercasePatternVsLowercaseNameChar(name: String, patternIndex: Int, nameIndex: Int): Boolean {
    return isUpperCase[patternIndex] && myPattern[patternIndex] != name[nameIndex]
  }

  private fun findUppercaseMatchFurther(
    name: String,
    patternIndex: Int,
    nameIndex: Int,
    isAsciiName: Boolean,
  ): PersistentList<TextRange>? {
    val nextWordStart = indexOfWordStart(name, patternIndex, nameIndex, isAsciiName)
    return matchWildcards(name, patternIndex, nextWordStart, isAsciiName)
  }

  private fun isFirstCharMatching(name: String, nameIndex: Int, patternIndex: Int): Boolean {
    if (nameIndex >= name.length) return false

    val ignoreCase = myOptions != NameUtil.MatchingCaseSensitivity.ALL
    val patternChar = myPattern[patternIndex]
    if (!charEquals(patternChar, patternIndex, name[nameIndex], ignoreCase)) return false

    if (myOptions == NameUtil.MatchingCaseSensitivity.FIRST_LETTER &&
        (patternIndex == 0 || patternIndex == 1 && isWildcard(0)) &&
        hasCase(patternChar) && patternChar.toChar().isUpperCase() != name[0].toChar().isUpperCase()
    ) {
      return false
    }
    return true
  }

  private fun isWildcard(patternIndex: Int): Boolean {
    if (patternIndex >= 0 && patternIndex < myPattern.size) {
      val pc = myPattern[patternIndex]
      return pc == ' ' || pc == '*'
    }
    return false
  }

  private fun isPatternChar(patternIndex: Int, c: Char): Boolean {
    return patternIndex >= 0 && patternIndex < myPattern.size && myPattern[patternIndex] == c
  }

  private fun indexOfWordStart(name: String, patternIndex: Int, startFrom: Int, isAsciiName: Boolean): Int {
    val p = myPattern[patternIndex]
    if (startFrom >= name.length ||
        myHasHumps && isLowerCase[patternIndex] && !(patternIndex > 0 && isWordSeparator[patternIndex - 1])
    ) {
      return -1
    }
    var i = startFrom
    val isSpecialSymbol = !p.toChar().isLetterOrDigit()
    while (true) {
      i = indexOfIgnoreCase(name, i, p, patternIndex, isAsciiName)
      if (i < 0) return -1

      if (isSpecialSymbol || NameUtilCore.isWordStart(name, i)) return i

      i++
    }
  }

  private fun indexOfIgnoreCase(name: String, fromIndex: Int, p: Char, patternIndex: Int, isAsciiName: Boolean): Int {
    if (isAsciiName && p.isAscii()) {
      val pUpper = toUpperCase[patternIndex]
      val pLower = toLowerCase[patternIndex]
      for (i in fromIndex..<name.length) {
        val c = name[i]
        if (c == pUpper || c == pLower) {
          return i
        }
      }
      return -1
    }
    return name.indexOf(p, startIndex = fromIndex, ignoreCase = true)
  }

  override fun toString(): String {
    return "MinusculeMatcherImpl{myPattern=" + myPattern.concatToString() + ", myOptions=" + myOptions + '}'
  }

  companion object {
    /** Camel-hump matching is >O(n), so for larger prefixes we fall back to simpler matching to avoid pauses  */
    private const val MAX_CAMEL_HUMP_MATCHING_LENGTH = 100

    private fun isWordSeparator(c: Char): Boolean {
      return c.toChar().isWhitespace() || c == '_' || c == '-' || c == ':' || c == '+' || c == '.'
    }

    private fun nextWord(name: String, start: Int): Int {
      if (start < name.length && name[start].isDigit()) {
        return start + 1 //treat each digit as a separate hump
      }
      return NameUtilCore.nextWord(name, start)
    }


    private fun prependRange(ranges: PersistentList<TextRange>, from: Int, length: Int): PersistentList<TextRange> {
      val head = ranges.firstOrNull()
      if (head != null && head.startOffset == from + length) {
        // Replace first with merged range
        return ranges.set(0, TextRange(from, head.endOffset))
      }
      return ranges.add(0, TextRange.from(from, length))
    }

    private fun filterWildcard(source: CharArray): CharArray {
      val buffer = CharArray(source.size)
      var i = 0
      for (c in source) {
        if (c != '*') buffer[i++] = c
      }

      return buffer.copyOf(i)
    }

    private fun isUpperCaseOrDigit(p: Char): Boolean {
      return p.isUpperCase() || p.isDigit()
    }

    private fun hasCase(patternChar: Char): Boolean {
      return patternChar.isUpperCase() || patternChar.isLowerCase()
    }
  }
}
