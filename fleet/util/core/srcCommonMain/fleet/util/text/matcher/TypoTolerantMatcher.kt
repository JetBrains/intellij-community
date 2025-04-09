// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package fleet.util.text.matcher

import fleet.util.BitSet
import kotlinx.collections.immutable.PersistentList
import kotlinx.collections.immutable.persistentListOf
import kotlin.math.min
import kotlin.math.pow

// Visible for testing
class TypoTolerantMatcher(
  pattern: String,
  private val myOptions: NameUtil.MatchingCaseSensitivity?,
  private val myHardSeparators: String,
) : MinusculeMatcher() {
  private val myPattern: CharArray
  private val myHasHumps: Boolean
  private val myHasSeparators: Boolean
  private val myHasDots: Boolean
  private val isLowerCase: BooleanArray
  private val isUpperCase: BooleanArray
  private val isWordSeparator: BooleanArray
  private val toUpperCase: CharArray
  private val toLowerCase: CharArray
  private val myMeaningfulCharacters: CharArray
  private val myMinNameLength: Int

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

  override fun matchingDegree(name: String, valueStartCaseMatch: Boolean): Int {
    return matchingDegree(name, valueStartCaseMatch, matchingFragments(name))
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

    var errors = 0
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
          nextHumpStart = nextWord(name, nextHumpStart, false)
        }

        val c = name[i]
        p = myPattern.indexOf(c, p + 1, myPattern.size, false)
        if (p < 0) {
          break
        }

        if (isHumpStart) {
          humpStartMatchedUpperCase = c == myPattern[p] && isUpperCase[p]
        }

        matchingCase += evaluateCaseMatching(valuedStartMatch, p, humpStartMatchedUpperCase, i, afterGap, isHumpStart, c)
      }

      errors = (errors + 2000.0 * (1.0 * (range as Range).errorCount / range.length).pow(2.0)).toInt()
    }

    val startIndex = first.startOffset
    val afterSeparator = name.indexOfAny(myHardSeparators, 0, startIndex) >= 0
    val wordStart = startIndex == 0 || NameUtilCore.isWordStart(name, startIndex) && !NameUtilCore.isWordStart(name, startIndex - 1)
    val finalMatch = fragments[fragments.size - 1].endOffset == name.length

    return (if (wordStart) 1000 else 0) +
           matchingCase -
           fragments.size +
           -skippedHumps * 10 -
           errors +
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

  override fun isStartMatch(name: String): Boolean {
    val fragments = matchingFragments(name)
    return fragments != null && isStartMatch(fragments)
  }

  override fun matches(name: String): Boolean {
    return matchingFragments(name) != null
  }

  override val pattern: String
    get() = myPattern.concatToString()

  override fun matchingFragments(name: String): PersistentList<TextRange>? {
    if (name.length < myMinNameLength) {
      return null
    }
    val ascii = name.isAscii()
    val ranges = Session(name, false, ascii).matchingFragments()
    if (ranges != null) return ranges

    return Session(name, true, ascii).matchingFragments()
  }

  private inner class Session(
    private val myName: String,
    private val myTypoAware: Boolean,
    private val isAsciiName: Boolean,
  ) {
    private val myAllowTypos = myTypoAware && isAsciiName

    fun charAt(i: Int, errorState: ErrorState): Char {
      return if (errorState.affects(i)) errorState.getChar(myPattern, i) else myPattern[i]
    }

    fun equalsIgnoreCase(patternIndex: Int, errorState: ErrorState, nameChar: Char): Boolean {
      if (errorState.affects(patternIndex)) {
        val patternChar = errorState.getChar(myPattern, patternIndex)
        return AsciiUtils.toLowerAscii(patternChar) == nameChar ||
               AsciiUtils.toUpperAscii(patternChar) == nameChar
      }
      return toLowerCase[patternIndex] == nameChar || toUpperCase[patternIndex] == nameChar
    }

    fun isLowerCase(i: Int, errorState: ErrorState): Boolean {
      return if (errorState.affects(i)) AsciiUtils.isLowerAscii(errorState.getChar(myPattern, i)) else isLowerCase[i]
    }

    fun isUpperCase(i: Int, errorState: ErrorState): Boolean {
      return if (errorState.affects(i)) AsciiUtils.isUpperAscii(errorState.getChar(myPattern, i)) else isUpperCase[i]
    }

    fun isWordSeparator(i: Int, errorState: ErrorState): Boolean {
      return if (errorState.affects(i)) isWordSeparator(errorState.getChar(myPattern, i)) else isWordSeparator[i]
    }

    fun patternLength(errorState: ErrorState): Int {
      return errorState.length(myPattern)
    }

    fun matchingFragments(): PersistentList<TextRange>? {
      val length = myName.length
      if (length < myMinNameLength) {
        return null
      }

      //we're in typo mode, but non-ascii symbols are used. so aborting
      if (myTypoAware && !isAsciiName) return null

      if (!myTypoAware) {
        var patternIndex = 0
        if (myMeaningfulCharacters.isNotEmpty()) {
          for (i in 0..<length) {
            val c = myName[i]
            if (c == myMeaningfulCharacters[patternIndex] || c == myMeaningfulCharacters[patternIndex + 1]) {
              patternIndex += 2
              if (patternIndex >= myMeaningfulCharacters.size) {
                break
              }
            }
          }
        }
        if (patternIndex < myMinNameLength * 2) {
          return null
        }
      }

      return matchWildcards(0, 0, ErrorState())
    }

    /**
     * After a wildcard (* or space), search for the first non-wildcard pattern character in the name starting from nameIndex
     * and try to [.matchFragment] for it.
     */
    fun matchWildcards(
      patternIndex: Int,
      nameIndex: Int,
      errorState: ErrorState,
    ): PersistentList<TextRange>? {
      var patternIndex = patternIndex
      if (nameIndex < 0) {
        return null
      }
      if (!isWildcard(patternIndex)) {
        if (patternIndex == patternLength(errorState)) {
          return persistentListOf()
        }
        return matchFragment(patternIndex, nameIndex, errorState)
      }

      do {
        patternIndex++
      }
      while (isWildcard(patternIndex))

      if (patternIndex == patternLength(errorState)) {
        // the trailing space should match if the pattern ends with the last word part, or only its first hump character
        if (isTrailingSpacePattern(errorState) && nameIndex != myName.length && (patternIndex < 2 || !isUpperCaseOrDigit(charAt(
            patternIndex - 2, errorState)))
        ) {
          val spaceIndex = myName.indexOf(' ', nameIndex)
          if (spaceIndex >= 0) {
            return persistentListOf(Range(spaceIndex, spaceIndex + 1, 0))
          }
          return null
        }
        return persistentListOf()
      }

      val ranges = matchFragment(patternIndex, nameIndex, errorState)
      if (ranges != null) {
        return ranges
      }

      return matchSkippingWords(patternIndex, nameIndex, true, errorState)
    }

    fun isTrailingSpacePattern(errorState: ErrorState): Boolean {
      return isPatternChar(patternLength(errorState) - 1, ' ', errorState)
    }

    /**
     * Enumerates places in name that could be matched by the pattern at patternIndex position
     * and invokes [.matchFragment] at those candidate positions
     */
    fun matchSkippingWords(
      patternIndex: Int,
      nameIndex: Int,
      allowSpecialChars: Boolean,
      errorState: ErrorState,
    ): PersistentList<TextRange>? {
      var nameIndex = nameIndex
      val wordStartsOnly = !isPatternChar(patternIndex - 1, '*', errorState) && !isWordSeparator(patternIndex, errorState)

      var maxFoundLength = 0
      while (true) {
        nameIndex = findNextPatternCharOccurrence(nameIndex, patternIndex, allowSpecialChars, wordStartsOnly, errorState)
        if (nameIndex < 0) {
          return null
        }
        val fragment = if (seemsLikeFragmentStart(patternIndex, nameIndex, errorState)) maxMatchingFragment(patternIndex, nameIndex,
                                                                                                            errorState)
        else null
        if (fragment == null) continue

        // match the remaining pattern only if we haven't already seen fragment of the same (or bigger) length
        // because otherwise it means that we already tried to match remaining pattern letters after it with the remaining name and failed
        // but now we have the same remaining pattern letters and even less remaining name letters, and so will fail as well
        val fragmentLength = fragment.length
        if (fragmentLength > maxFoundLength || nameIndex + fragmentLength == myName.length && isTrailingSpacePattern(errorState)) {
          if (!isMiddleMatch(patternIndex, nameIndex, errorState)) {
            maxFoundLength = fragmentLength
          }
          val ranges = matchInsideFragment(patternIndex, nameIndex, fragment)
          if (ranges != null) {
            return ranges
          }
        }
      }
    }

    fun findNextPatternCharOccurrence(
      startAt: Int,
      patternIndex: Int,
      allowSpecialChars: Boolean,
      wordStartsOnly: Boolean,
      errorState: ErrorState,
    ): Int {
      val next = if (wordStartsOnly)
        indexOfWordStart(patternIndex, startAt, errorState)
      else
        indexOfIgnoreCase(startAt + 1, patternIndex, errorState)

      // pattern humps are allowed to match in words separated by " ()", lowercase characters aren't
      if (!allowSpecialChars && !myHasSeparators && !myHasHumps && myName.indexOfAny(myHardSeparators, startAt, next) >= 0) {
        return -1
      }
      // if the user has typed a dot, don't skip other dots between humps
      // but one pattern dot may match several name dots
      if (!allowSpecialChars && myHasDots && !isPatternChar(patternIndex - 1, '.', errorState) && myName.indexOf('.', startAt, next) >= 0) {
        return -1
      }

      return next
    }

    fun seemsLikeFragmentStart(patternIndex: Int, nextOccurrence: Int, errorState: ErrorState): Boolean {
      // uppercase should match either uppercase or a word start
      return !isUpperCase(patternIndex, errorState) ||
             myName[nextOccurrence].toChar().isUpperCase() ||
             NameUtilCore.isWordStart(myName,
                                      nextOccurrence) ||  // accept uppercase matching lowercase if the whole prefix is uppercase and case sensitivity allows that
             !myHasHumps && myOptions != NameUtil.MatchingCaseSensitivity.ALL
    }

    fun charEquals(
      patternIndex: Int,
      nameIndex: Int,
      isIgnoreCase: Boolean,
      allowTypos: Boolean,
      errorState: ErrorState,
    ): Boolean {
      val patternChar = charAt(patternIndex, errorState)
      val nameChar = myName[nameIndex]
      val length = myName.length

      if (patternChar == nameChar || isIgnoreCase && equalsIgnoreCase(patternIndex, errorState, nameChar)) {
        return true
      }

      if (!myAllowTypos || !allowTypos) return false

      if (errorState.countErrors(0, patternIndex) > 0) return false
      val prevError = errorState.getError(patternIndex - 1)
      if (prevError === SwapError.Companion.instance) {
        return false
      }

      val leftMiss: Char = leftMiss(patternChar)
      if (leftMiss.code != 0) {
        if (leftMiss == nameChar ||
            isIgnoreCase && (AsciiUtils.toLowerAscii(leftMiss) == nameChar || AsciiUtils.toUpperAscii(leftMiss) == nameChar)
        ) {
          errorState.addError(patternIndex, TypoError(leftMiss))
          return true
        }
      }

      val rightMiss: Char = rightMiss(patternChar)
      if (rightMiss.code != 0) {
        if (rightMiss == nameChar ||
            isIgnoreCase && (AsciiUtils.toLowerAscii(rightMiss) == nameChar || AsciiUtils.toUpperAscii(rightMiss) == nameChar)
        ) {
          errorState.addError(patternIndex, TypoError(rightMiss))
          return true
        }
      }

      if (patternLength(errorState) > patternIndex + 1 && length > nameIndex + 1) {
        val nextNameChar = myName[nameIndex + 1]
        val nextPatternChar = charAt(patternIndex + 1, errorState)

        if ((patternChar == nextNameChar || isIgnoreCase && equalsIgnoreCase(patternIndex, errorState, nextNameChar)) &&
            (nextPatternChar == nameChar || isIgnoreCase && equalsIgnoreCase(patternIndex + 1, errorState, nameChar))
        ) {
          errorState.addError(patternIndex, SwapError.Companion.instance)
          return true
        }
      }

      if (length > nameIndex + 1) {
        val nextNameChar = myName[nameIndex + 1]

        if (patternChar == nextNameChar || isIgnoreCase && equalsIgnoreCase(patternIndex, errorState, nextNameChar)) {
          errorState.addError(patternIndex, MissError(nameChar))
          return true
        }
      }

      return false
    }

    fun matchFragment(
      patternIndex: Int,
      nameIndex: Int,
      errorState: ErrorState,
    ): PersistentList<TextRange>? {
      val fragment = maxMatchingFragment(patternIndex, nameIndex, errorState)
      return if (fragment == null) null else matchInsideFragment(patternIndex, nameIndex, fragment)
    }

    fun maxMatchingFragment(patternIndex: Int, nameIndex: Int, baseErrorState: ErrorState): Fragment? {
      val errorState = baseErrorState.deriveFrom(patternIndex)

      if (!isFirstCharMatching(nameIndex, patternIndex, errorState)) {
        return null
      }

      var i = 1
      val ignoreCase = myOptions != NameUtil.MatchingCaseSensitivity.ALL
      while (nameIndex + i < myName.length && patternIndex + i < patternLength(errorState)) {
        if (!charEquals(patternIndex + i, nameIndex + i, ignoreCase, true, errorState)) {
          if (charAt(patternIndex + i, errorState).toChar().isDigit() && charAt(patternIndex + i - 1, errorState).toChar().isDigit()) {
            return null
          }
          break
        }
        i++
      }
      return Fragment(i, errorState)
    }

    // we've found the longest fragment matching pattern and name
    fun matchInsideFragment(
      patternIndex: Int,
      nameIndex: Int,
      fragment: Fragment,
    ): PersistentList<TextRange>? {
      // exact middle matches have to be at least of length 3, to prevent too many irrelevant matches
      val minFragment = if (isMiddleMatch(patternIndex, nameIndex, fragment.errorState))
        3
      else
        1

      val camelHumpRanges = improveCamelHumps(patternIndex, nameIndex, fragment.length, minFragment,
                                              fragment.errorState)
      if (camelHumpRanges != null) {
        return camelHumpRanges
      }

      return findLongestMatchingPrefix(patternIndex, nameIndex, fragment.length, minFragment, fragment.errorState)
    }

    fun isMiddleMatch(patternIndex: Int, nameIndex: Int, errorState: ErrorState): Boolean {
      return isPatternChar(patternIndex - 1, '*', errorState) && !isWildcard(patternIndex + 1) &&
             myName[nameIndex].toChar().isLetterOrDigit() && !NameUtilCore.isWordStart(myName, nameIndex)
    }

    fun findLongestMatchingPrefix(
      patternIndex: Int,
      nameIndex: Int,
      fragmentLength: Int, minFragment: Int,
      errorState: ErrorState,
    ): PersistentList<TextRange>? {
      if (patternIndex + fragmentLength >= patternLength(errorState)) {
        val errors = errorState.countErrors(patternIndex, patternIndex + fragmentLength)
        if (errors == fragmentLength) return null
        return persistentListOf(Range(nameIndex, nameIndex + fragmentLength, errors))
      }

      // try to match the remainder of pattern with the remainder of name
      // it may not succeed with the longest matching fragment, then try shorter matches
      var i = fragmentLength
      while (i >= minFragment || isWildcard(patternIndex + i)) {
        val derivedErrorState = errorState.deriveFrom(patternIndex + i)
        val ranges = if (isWildcard(patternIndex + i)) matchWildcards(patternIndex + i, nameIndex + i,
                                                                      derivedErrorState)
        else matchSkippingWords(patternIndex + i, nameIndex + i, false, derivedErrorState)
        if (ranges != null) {
          val errors = errorState.countErrors(patternIndex, patternIndex + i)
          if (errors == i) return null
          return prependRange(ranges, Range(nameIndex, nameIndex + i, errors))
        }
        i--
      }
      return null
    }

    /**
     * When pattern is "CU" and the name is "CurrentUser", we already have a prefix "Cu" that matches,
     * but we try to find uppercase "U" later in name for better matching degree
     */
    fun improveCamelHumps(
      patternIndex: Int,
      nameIndex: Int,
      maxFragment: Int,
      minFragment: Int,
      errorState: ErrorState,
    ): PersistentList<TextRange>? {
      for (i in minFragment..<maxFragment) {
        if (isUppercasePatternVsLowercaseNameChar(patternIndex + i, nameIndex + i, errorState)) {
          val ranges = findUppercaseMatchFurther(patternIndex + i, nameIndex + i,
                                                 errorState.deriveFrom(patternIndex + i))
          if (ranges != null) {
            val errors = errorState.countErrors(patternIndex, patternIndex + i)
            if (errors == i) return null
            return prependRange(ranges, Range(nameIndex, nameIndex + i, errors))
          }
        }
      }
      return null
    }

    fun isUppercasePatternVsLowercaseNameChar(patternIndex: Int, nameIndex: Int, errorState: ErrorState): Boolean {
      return isUpperCase(patternIndex, errorState) && !charEquals(patternIndex, nameIndex, false, false, errorState)
    }

    fun findUppercaseMatchFurther(
      patternIndex: Int,
      nameIndex: Int,
      errorState: ErrorState,
    ): PersistentList<TextRange>? {
      val nextWordStart = indexOfWordStart(patternIndex, nameIndex, errorState)
      return matchWildcards(patternIndex, nextWordStart, errorState.deriveFrom(patternIndex))
    }

    fun isFirstCharMatching(nameIndex: Int, patternIndex: Int, errorState: ErrorState): Boolean {
      if (nameIndex >= myName.length) return false

      val ignoreCase = myOptions != NameUtil.MatchingCaseSensitivity.ALL
      if (!charEquals(patternIndex, nameIndex, ignoreCase, true, errorState)) return false

      val patternChar = charAt(patternIndex, errorState)

      return !(myOptions == NameUtil.MatchingCaseSensitivity.FIRST_LETTER &&
               (patternIndex == 0 || patternIndex == 1 && isWildcard(0)) &&
               hasCase(patternChar) && patternChar.toChar().isUpperCase() != myName[0].toChar().isUpperCase())
    }

    fun isPatternChar(patternIndex: Int, c: Char, errorState: ErrorState): Boolean {
      return patternIndex >= 0 && patternIndex < patternLength(errorState) && charAt(patternIndex, errorState) == c
    }

    fun indexOfWordStart(patternIndex: Int, startFrom: Int, errorState: ErrorState): Int {
      if (startFrom >= myName.length ||
          myHasHumps && isLowerCase(patternIndex, errorState) && !(patternIndex > 0 && isWordSeparator(patternIndex - 1, errorState))
      ) {
        return -1
      }
      var nextWordStart = startFrom
      while (true) {
        nextWordStart = nextWord(myName, nextWordStart, isAsciiName)
        if (nextWordStart >= myName.length) {
          return -1
        }
        if (charEquals(patternIndex, nextWordStart, true, true, errorState)) {
          return nextWordStart
        }
      }
    }

    fun indexOfIgnoreCase(fromIndex: Int, patternIndex: Int, errorState: ErrorState): Int {
      val p = charAt(patternIndex, errorState)
      if (isAsciiName && p.isAscii()) {
        val i = indexIgnoringCaseAscii(fromIndex, p)
        if (i != -1) return i

        if (myAllowTypos) {
          val leftMiss = indexIgnoringCaseAscii(fromIndex, leftMiss(p))
          if (leftMiss != -1) return leftMiss

          val rightMiss = indexIgnoringCaseAscii(fromIndex, rightMiss(p))
          if (rightMiss != -1) return rightMiss
        }

        return -1
      }

      return myName.indexOf(p, fromIndex, ignoreCase = true)
    }

    fun indexIgnoringCaseAscii(fromIndex: Int, p: Char): Int {
      val pUpper = AsciiUtils.toUpperAscii(p)
      val pLower = AsciiUtils.toLowerAscii(p)
      for (i in fromIndex..<myName.length) {
        val c = myName[i]
        if (c == p || AsciiUtils.toUpperAscii(c) == pUpper || AsciiUtils.toLowerAscii(c) == pLower) {
          return i
        }
      }
      return -1
    }


  }

  private fun isWildcard(patternIndex: Int): Boolean {
    if (patternIndex >= 0 && patternIndex < myPattern.size) {
      val pc = myPattern[patternIndex]
      return pc == ' ' || pc == '*'
    }
    return false
  }

  override fun toString(): String {
    return "TypoTolerantMatcher{myPattern=" + myPattern.concatToString() + ", myOptions=" + myOptions + '}'
  }

  private data class ErrorWithIndex(val index: Int, val error: Error?)

  private class ErrorState(
    private val myBase: ErrorState? = null,
    private val myDeriveIndex: Int = 0,
  ) {
    private var myAffected: BitSet? = null
    private var myAllAffectedAfter = Int.Companion.MAX_VALUE
    private var myErrors: MutableList<ErrorWithIndex>? = null
    private var myPattern: CharArray? = null

    fun deriveFrom(index: Int): ErrorState {
      return ErrorState(this, index)
    }

    fun addError(index: Int, error: Error) {
      if (myErrors == null) {
        myErrors = mutableListOf()
        myAffected = BitSet()
      }
      val errorWithIndex = ErrorWithIndex(index, error)
      myErrors!!.add(errorWithIndex)
      updateAffected(index, error)

      if (myPattern != null) {
        myPattern = applyError(myPattern!!, errorWithIndex)
      }
    }

    fun updateAffected(index: Int, error: Error) {
      myAffected!!.set(index, index + 1)
      if (error is SwapError) {
        myAffected!!.set(index + 1, index + 2)
      }
      else if (error is MissError) {
        myAllAffectedAfter = min(index.toDouble(), myAllAffectedAfter.toDouble()).toInt()
      }
    }

    fun countErrors(start: Int, end: Int): Int {
      var errors = 0
      if (myBase != null && start < myDeriveIndex) {
        errors += myBase.countErrors(start, myDeriveIndex)
      }

      if (myErrors != null) {
        for (error in myErrors) {
          if (start <= error.index && error.index < end) {
            errors++
          }
        }
      }

      return errors
    }

    fun getChar(pattern: CharArray, index: Int): Char {
      if (myPattern == null) {
        myPattern = applyErrors(pattern.copyOf(), Int.Companion.MAX_VALUE)
      }

      return myPattern!![index]
    }

    fun applyErrors(pattern: CharArray, upToIndex: Int): CharArray {
      var pattern = pattern
      if (myBase != null) {
        pattern = myBase.applyErrors(pattern, min(myDeriveIndex.toDouble(), upToIndex.toDouble()).toInt())
      }

      if (myErrors != null) {
        for (error in myErrors) {
          if (error.index < upToIndex) {
            pattern = applyError(pattern, error)
          }
        }
      }

      return pattern
    }

    fun affects(index: Int): Boolean {
      return localAffects(index) || (myBase != null && myBase.affects(index))
    }

    fun localAffects(index: Int): Boolean {
      return index >= myAllAffectedAfter || myAffected != null && myAffected!!.get(index)
    }

    fun getError(i: Int): Error? {
      if (myErrors != null && myAffected!!.get(i)) {
        for (error in myErrors) {
          if (error.index == i) return error.error
        }
      }

      if (myBase != null && myDeriveIndex > i) {
        return myBase.getError(i)
      }

      return null
    }

    fun numMisses(end: Int): Int {
      var numMisses = 0
      if (myErrors != null && end > 0) {
        for (error in myErrors) {
          if (error.index < end && error.error is MissError) {
            numMisses++
          }
        }
      }
      return numMisses + (myBase?.numMisses(myDeriveIndex) ?: 0)
    }

    fun length(pattern: CharArray): Int {
      if (myPattern != null) {
        return myPattern!!.size
      }
      return pattern.size + numMisses(Int.Companion.MAX_VALUE)
    }

    companion object {
      private fun applyError(pattern: CharArray, error: ErrorWithIndex): CharArray {
        when (val actualError = error.error) {
          is TypoError -> {
            pattern[error.index] = actualError.correctChar
            return pattern
          }
          is SwapError -> {
            val index = error.index
            val c = pattern[index]
            pattern[index] = pattern[index + 1]
            pattern[index + 1] = c
            return pattern
          }
          is MissError -> {
            val newArray = CharArray(pattern.size + 1)
            pattern.copyInto(newArray, endIndex = error.index)
            newArray[error.index] = actualError.missedChar
            pattern.copyInto(newArray, destinationOffset = error.index + 1, startIndex = error.index)
            return newArray
          }
          else -> return pattern
        }
      }
    }
  }

  private interface Error

  private data class TypoError(val correctChar: Char) : Error
  private class SwapError : Error {
    companion object {
      val instance: SwapError = SwapError()
    }
  }

  private data class MissError(val missedChar: Char) : Error

  private class Fragment(val length: Int, val errorState: ErrorState)

  private class Range(startOffset: Int, endOffset: Int, val errorCount: Int) : TextRange(startOffset, endOffset) {
    override fun shiftRight(delta: Int): Range {
      if (delta == 0) return this
      return Range(startOffset + delta, endOffset + delta, this.errorCount)
    }
  }


  /**
   * Constructs a matcher by a given pattern.
   * @param pattern the pattern
   * @param myOptions case sensitivity settings
   * @param myHardSeparators A string of characters (empty by default). Lowercase humps don't work for parts separated by any of these characters.
   * Need either an explicit uppercase letter or the same separator character in prefix
   */
  init {
    myPattern = pattern.removeSuffix("* ").toCharArray()
    isLowerCase = BooleanArray(myPattern.size)
    isUpperCase = BooleanArray(myPattern.size)
    isWordSeparator = BooleanArray(myPattern.size)
    toUpperCase = CharArray(myPattern.size)
    toLowerCase = CharArray(myPattern.size)
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

  companion object {
    private fun isWordSeparator(c: Char): Boolean {
      return c.isWhitespace() || c == '_' || c == '-' || c == ':' || c == '+' || c == '.'
    }

    private fun nextWord(name: String, start: Int, isAsciiName: Boolean): Int {
      if (start < name.length && name[start].isDigit()) {
        return start + 1 //treat each digit as a separate hump
      }
      if (isAsciiName) {
        return AsciiUtils.nextWordAscii(name, start)
      }
      return NameUtilCore.nextWord(name, start)
    }

    private fun prependRange(ranges: PersistentList<TextRange>, range: Range): PersistentList<TextRange> {
      val head = ranges.firstOrNull() as? Range
      return if (head != null && head.startOffset == range.endOffset) {
        ranges.set(0, Range(range.startOffset, head.endOffset, range.errorCount + head.errorCount))
      }
      else {
        ranges.add(0, range)
      }
    }

    private val keyboard = arrayOf<CharArray>(charArrayOf('q', 'w', 'e', 'r', 't', 'y', 'u', 'i', 'o', 'p'),
                                              charArrayOf('a', 's', 'd', 'f', 'g', 'h', 'j', 'k', 'l'),
                                              charArrayOf('z', 'x', 'c', 'v', 'b', 'n', 'm')
    )

    private fun leftMiss(aChar: Char): Char {
      val isUpperCase = AsciiUtils.isUpperAscii(aChar)
      val lc = if (isUpperCase) AsciiUtils.toLowerAscii(aChar) else aChar

      for (line in keyboard) {
        for (j in line.indices) {
          val c = line[j]
          if (c == lc) {
            if (j > 0) {
              return if (isUpperCase) AsciiUtils.toUpperAscii(line[j - 1]) else line[j - 1]
            }
            else {
              return 0.toChar()
            }
          }
        }
      }
      return 0.toChar()
    }

    private fun rightMiss(aChar: Char): Char {
      val isUpperCase = AsciiUtils.isUpperAscii(aChar)
      val lc = if (isUpperCase) AsciiUtils.toLowerAscii(aChar) else aChar

      for (line in keyboard) {
        for (j in line.indices) {
          val c = line[j]
          if (c == lc) {
            if (j + 1 < line.size) {
              return if (isUpperCase) AsciiUtils.toUpperAscii(line[j + 1]) else line[j + 1]
            }
            else {
              return 0.toChar()
            }
          }
        }
      }
      return 0.toChar()
    }

    private fun isUpperCaseOrDigit(p: Char): Boolean {
      return p.isUpperCase() || p.isDigit()
    }

    private fun hasCase(patternChar: Char): Boolean {
      return patternChar.isUpperCase() || patternChar.isLowerCase()
    }
  }
}
