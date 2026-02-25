// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jewel.foundation.search.impl

import org.jetbrains.jewel.foundation.search.MatchingCaseSensitivity
import org.jetbrains.jewel.foundation.search.SpeedSearchMatcher
import org.jetbrains.jewel.foundation.search.SpeedSearchMatcher.MatchResult

/**
 * Tells whether a string matches a specific pattern. Allows for lowercase camel-hump matching.
 *
 * **Swing equivalent:**
 * [MinusculeMatcherImpl](https://github.com/JetBrains/intellij-community/blob/master/platform/util/text-matching/src/com/intellij/psi/codeStyle/MinusculeMatcherImpl.java)
 *
 * **Helper functions based on:**
 * [NameUtilCore](https://github.com/JetBrains/intellij-community/blob/master/platform/util/base/src/com/intellij/util/text/NameUtilCore.java)
 */
internal class PatternSpeedSearchMatcher(
    basePattern: String,
    private val options: MatchingCaseSensitivity,
    private val ignoredSeparators: String,
    private val containsMatcher: SpeedSearchMatcher,
) : SpeedSearchMatcher {
    private val pattern: CharArray = basePattern.trimEnd('*', ' ').toCharArray()
    private val isLowerCase: BooleanArray = BooleanArray(basePattern.length)
    private val isUpperCase: BooleanArray = BooleanArray(basePattern.length)
    private val isWordSeparator: BooleanArray = BooleanArray(basePattern.length)
    private val patternUpperCase: CharArray = CharArray(basePattern.length)
    private val patternLowerCase: CharArray = CharArray(basePattern.length)
    private val ignoreCase = options != MatchingCaseSensitivity.All

    private val hasHumps: Boolean
    private val hasSeparators: Boolean
    private val hasDots: Boolean
    private val meaningfulCharacters: CharArray
    private val minMatchingTextLength: Int

    init {
        val meaningful = buildString {
            for (k in basePattern.indices) {
                val c = basePattern[k]
                isLowerCase[k] = c.isLowerCase()
                isUpperCase[k] = c.isUpperCase()
                isWordSeparator[k] = c.isWordSeparator()
                patternUpperCase[k] = c.uppercaseChar()
                patternLowerCase[k] = c.lowercaseChar()

                if (!isWildcard(k)) {
                    append(patternLowerCase[k])
                    append(patternUpperCase[k])
                }
            }
        }

        var start = 0
        while (isWildcard(start)) start++
        hasHumps = isLowerCase.hasAnyTrueValueStartingFrom(start) && isUpperCase.hasAnyTrueValueStartingFrom(start + 1)
        hasSeparators = isWordSeparator.hasAnyTrueValueStartingFrom(start)
        hasDots = (start until pattern.size).any { pattern[it] == '.' }
        meaningfulCharacters = meaningful.toCharArray()
        minMatchingTextLength = meaningfulCharacters.size / 2
    }

    override fun matches(text: String?): MatchResult = matches(text as? CharSequence)

    override fun matches(text: CharSequence?): MatchResult =
        if (text.isNullOrBlank()) {
            MatchResult.NoMatch
        } else {
            text.matchingFragments()
        }

    private fun CharSequence.matchingFragments(): MatchResult {
        if (length < minMatchingTextLength) {
            return MatchResult.NoMatch
        }

        if (pattern.size > MAX_CAMEL_HUMP_MATCHING_LENGTH) {
            return containsMatcher.matches(this)
        }

        var patternIndex = 0
        for (i in 0 until length) {
            if (patternIndex >= meaningfulCharacters.size) break
            val c = this[i]
            if (c == meaningfulCharacters[patternIndex] || c == meaningfulCharacters[patternIndex + 1]) {
                patternIndex += 2
            }
        }
        if (patternIndex < minMatchingTextLength * 2) {
            return MatchResult.NoMatch
        }
        return MatchResult.from(matchWildcards(0, 0, isAscii()))
    }

    /**
     * After a wildcard (* or space), search for the first non-wildcard pattern character in the string starting from
     * currentIndex and try to [matchFragment] for it.
     */
    private fun CharSequence.matchWildcards(patternIndex: Int, currentIndex: Int, isAscii: Boolean): List<IntRange>? {
        if (currentIndex < 0) {
            return null
        }
        if (!isWildcard(patternIndex)) {
            if (patternIndex == pattern.size) {
                return null
            }
            return matchFragment(patternIndex, currentIndex, isAscii)
        }

        var currentPatternIndex = patternIndex
        do {
            currentPatternIndex++
        } while (isWildcard(currentPatternIndex))

        if (currentPatternIndex == pattern.size) {
            // Suppressing condition to keep the structure as similar as the Java/Swing files as possible
            @Suppress("ComplexCondition")
            if (
                isTrailingSpacePattern() &&
                    currentIndex != length &&
                    (currentPatternIndex < 2 || !pattern[currentPatternIndex - 2].isUpperCaseOrDigit())
            ) {
                val spaceIndex = indexOf(' ', currentIndex)
                if (spaceIndex >= 0) {
                    return listOf(IntRange.from(spaceIndex, 1))
                }
            }
            return null
        }

        return matchSkippingWords(
            currentPatternIndex,
            findNextPatternCharOccurrence(currentIndex, currentPatternIndex, isAscii),
            allowSpecialChars = true,
            isAscii,
        )
    }

    private fun isTrailingSpacePattern(): Boolean = ' '.isPatternChar(pattern.size - 1)

    /**
     * Enumerates places in the string that could be matched by the pattern at patternIndex position and invokes
     * [matchFragment] at those candidate positions
     */
    private fun CharSequence.matchSkippingWords(
        patternIndex: Int,
        currentIndex: Int,
        allowSpecialChars: Boolean,
        isAscii: Boolean,
    ): List<IntRange>? {
        var currentNameIndex = currentIndex
        var maxFoundLength = 0
        while (currentNameIndex >= 0) {
            val fragmentLength =
                if (seemsLikeFragmentStart(patternIndex, currentNameIndex)) {
                    maxMatchingFragment(patternIndex, currentNameIndex)
                } else {
                    0
                }

            // match the remaining pattern only if we haven't already seen fragment of the same (or bigger) length
            // because otherwise it means that we already tried to match remaining pattern letters after it with the
            // remaining text and failed but now we have the same remaining pattern letters and even less remaining
            // string letters, and so will fail as well
            if (
                fragmentLength > maxFoundLength ||
                    currentNameIndex + fragmentLength == length && isTrailingSpacePattern()
            ) {
                if (!isMiddleMatch(patternIndex, currentNameIndex)) {
                    maxFoundLength = fragmentLength
                }
                val ranges = matchInsideFragment(patternIndex, currentNameIndex, isAscii, fragmentLength)
                if (ranges != null) {
                    return ranges
                }
            }
            val next = findNextPatternCharOccurrence(currentNameIndex + 1, patternIndex, isAscii)
            currentNameIndex =
                if (allowSpecialChars) next else checkForSpecialChars(currentNameIndex + 1, next, patternIndex)
        }
        return null
    }

    private fun CharSequence.findNextPatternCharOccurrence(startAt: Int, patternIndex: Int, isAscii: Boolean): Int =
        if (!'*'.isPatternChar(patternIndex - 1) && !isWordSeparator[patternIndex]) {
            indexOfWordStart(patternIndex, startAt, isAscii)
        } else {
            indexOfIgnoreCase(startAt, pattern[patternIndex], patternIndex, isAscii)
        }

    private fun CharSequence.checkForSpecialChars(start: Int, end: Int, patternIndex: Int): Int {
        if (end < 0 || end < start) return -1

        // pattern humps are allowed to match in words separated by " ()", lowercase characters aren't
        if (!hasSeparators && !hasHumps && substring(start, end).any { ignoredSeparators.contains(it) }) {
            return -1
        }

        // if the user has typed a dot, don't skip other dots between humps, but one pattern dot may match several text
        // dots
        if (hasDots && !'.'.isPatternChar(patternIndex - 1) && substring(start, end).contains('.')) {
            return -1
        }

        return end
    }

    private fun CharSequence.seemsLikeFragmentStart(patternIndex: Int, nextOccurrence: Int): Boolean =
        !isUpperCase[patternIndex] ||
            this[nextOccurrence].isUpperCase() ||
            isWordStartingAt(nextOccurrence) ||
            !hasHumps && ignoreCase

    private fun Char.matchPatternChar(patternChar: Char, patternIndex: Int): Boolean =
        patternChar == this ||
            ignoreCase && (patternLowerCase[patternIndex] == this || patternUpperCase[patternIndex] == this)

    private fun CharSequence.matchFragment(patternIndex: Int, currentIndex: Int, isAscii: Boolean): List<IntRange>? {
        val fragmentLength = maxMatchingFragment(patternIndex, currentIndex)
        return if (fragmentLength == 0) {
            null
        } else {
            matchInsideFragment(patternIndex, currentIndex, isAscii, fragmentLength)
        }
    }

    private fun CharSequence.maxMatchingFragment(patternIndex: Int, currentIndex: Int): Int {
        if (!isFirstCharMatching(currentIndex, patternIndex)) {
            return 0
        }

        var index = 1
        while (currentIndex + index < length && patternIndex + index < pattern.size) {
            val char = this[currentIndex + index]
            if (!char.matchPatternChar(pattern[patternIndex + index], patternIndex + index)) {
                if (char.isSkippingDigitBetweenPatternDigits(patternIndex + index)) {
                    return 0
                }
                break
            }
            index++
        }
        return index
    }

    private fun Char.isSkippingDigitBetweenPatternDigits(patternIndex: Int): Boolean =
        pattern[patternIndex].isDigit() && pattern[patternIndex - 1].isDigit() && isDigit()

    // we've found the longest fragment matching the pattern in the string
    private fun CharSequence.matchInsideFragment(
        patternIndex: Int,
        currentIndex: Int,
        isAscii: Boolean,
        fragmentLength: Int,
    ): List<IntRange>? {
        // exact middle matches have to be at least of length 3, to prevent too many irrelevant matches
        val minFragment = if (isMiddleMatch(patternIndex, currentIndex)) 3 else 1

        val camelHumpRanges = improveCamelHumps(patternIndex, currentIndex, isAscii, fragmentLength, minFragment)
        if (camelHumpRanges != null) {
            return camelHumpRanges
        }

        return findLongestMatchingPrefix(patternIndex, currentIndex, isAscii, fragmentLength, minFragment)
    }

    private fun CharSequence.isMiddleMatch(patternIndex: Int, currentIndex: Int): Boolean =
        '*'.isPatternChar(patternIndex - 1) &&
            !isWildcard(patternIndex + 1) &&
            this[currentIndex].isLetterOrDigit() &&
            !isWordStartingAt(currentIndex)

    private fun CharSequence.findLongestMatchingPrefix(
        patternIndex: Int,
        currentIndex: Int,
        isAscii: Boolean,
        fragmentLength: Int,
        minFragment: Int,
    ): List<IntRange>? {
        if (patternIndex + fragmentLength >= pattern.size) {
            return listOf(IntRange.from(currentIndex, fragmentLength))
        }

        // try to match the remainder of the pattern with the remainder of string
        // it may not succeed with the longest matching fragment, then try shorter matches
        var length = fragmentLength
        while (length >= minFragment || (length > 0 && isWildcard(patternIndex + length))) {
            val ranges: List<IntRange>? =
                if (isWildcard(patternIndex + length)) {
                    matchWildcards(patternIndex + length, currentIndex + length, isAscii)
                } else {
                    var nextOccurrence =
                        findNextPatternCharOccurrence(currentIndex + length + 1, patternIndex + length, isAscii)
                    nextOccurrence = checkForSpecialChars(currentIndex + length, nextOccurrence, patternIndex + length)
                    if (nextOccurrence >= 0) {
                        matchSkippingWords(patternIndex + length, nextOccurrence, false, isAscii)
                    } else {
                        null
                    }
                }
            if (ranges != null) {
                return prependRange(ranges, currentIndex, length)
            }
            length--
        }
        return null
    }

    /**
     * When pattern is "CU" and the string is "CurrentUser", we already have a prefix "Cu" that matches, but we try to
     * find uppercase "U" later in string for better matching degree
     */
    private fun CharSequence.improveCamelHumps(
        patternIndex: Int,
        currentIndex: Int,
        isAscii: Boolean,
        maxFragment: Int,
        minFragment: Int,
    ): List<IntRange>? {
        for (i in minFragment until maxFragment) {
            if (isUppercasePatternVsLowercaseNameChar(patternIndex + i, currentIndex + i)) {
                val ranges = findUppercaseMatchFurther(patternIndex + i, currentIndex + i, isAscii)
                if (ranges != null) {
                    return prependRange(ranges, currentIndex, i)
                }
            }
        }
        return null
    }

    private fun CharSequence.isUppercasePatternVsLowercaseNameChar(patternIndex: Int, currentIndex: Int): Boolean =
        isUpperCase[patternIndex] && pattern[patternIndex] != this[currentIndex]

    private fun CharSequence.findUppercaseMatchFurther(
        patternIndex: Int,
        currentIndex: Int,
        isAscii: Boolean,
    ): List<IntRange>? {
        val nextWordStart = indexOfWordStart(patternIndex, currentIndex, isAscii)
        return matchWildcards(patternIndex, nextWordStart, isAscii)
    }

    private fun CharSequence.isFirstCharMatching(currentIndex: Int, patternIndex: Int): Boolean {
        if (currentIndex >= length) return false

        val patternChar = pattern[patternIndex]
        if (!this[currentIndex].matchPatternChar(patternChar, patternIndex)) return false

        // Suppressing condition to keep the structure as similar as the Java/Swing files as possible
        @Suppress("ComplexCondition")
        if (
            options == MatchingCaseSensitivity.FirstLetter &&
                (patternIndex == 0 || patternIndex == 1 && isWildcard(0)) &&
                patternChar.hasCase() &&
                patternChar.isUpperCase() != this[0].isUpperCase()
        ) {
            return false
        }
        return true
    }

    private fun isWildcard(patternIndex: Int): Boolean {
        if (patternIndex >= 0 && patternIndex < pattern.size) {
            val currentChar = pattern[patternIndex]
            return currentChar == ' ' || currentChar == '*'
        }
        return false
    }

    private fun Char.isPatternChar(patternIndex: Int): Boolean =
        (patternIndex >= 0) && (patternIndex < pattern.size) && (pattern[patternIndex] == this)

    private fun CharSequence.indexOfWordStart(patternIndex: Int, startFrom: Int, isAscii: Boolean): Int {
        val p = pattern[patternIndex]

        // Suppressing condition to keep the structure as similar as the Java/Swing files as possible
        @Suppress("ComplexCondition")
        if (
            startFrom >= length ||
                hasHumps && isLowerCase[patternIndex] && !(patternIndex > 0 && isWordSeparator[patternIndex - 1])
        ) {
            return -1
        }
        var fromIndex = startFrom
        val isSpecialSymbol = !p.isLetterOrDigit()
        while (true) {
            fromIndex = indexOfIgnoreCase(fromIndex, p, patternIndex, isAscii)
            if (fromIndex < 0) return -1

            if (isSpecialSymbol || isWordStartingAt(fromIndex)) return fromIndex

            fromIndex++
        }
    }

    private fun CharSequence.indexOfIgnoreCase(fromIndex: Int, p: Char, patternIndex: Int, isAscii: Boolean): Int {
        if (isAscii && p.code < 128) {
            val pUpper = patternUpperCase[patternIndex]
            val pLower = patternLowerCase[patternIndex]
            for (i in fromIndex until length) {
                val c = this[i]
                if (c == pUpper || c == pLower) {
                    return i
                }
            }
            return -1
        }
        return indexOf(p, fromIndex, ignoreCase = true)
    }

    private fun BooleanArray.hasAnyTrueValueStartingFrom(startIndex: Int): Boolean {
        for (i in startIndex until pattern.size) {
            if (this[i]) {
                return true
            }
        }
        return false
    }

    private fun prependRange(ranges: List<IntRange>, from: Int, length: Int): List<IntRange> {
        val head = ranges.firstOrNull()
        return if (head != null && head.first == from + length) {
            val tail = ranges.drop(1)
            tail + listOf(IntRange(from, head.last))
        } else {
            listOf(IntRange.from(from, length)) + ranges
        }
    }
}

private fun IntRange.Companion.from(startOffset: Int, length: Int) = startOffset until startOffset + length

private fun Char.isUpperCaseOrDigit(): Boolean = isUpperCase() || isDigit()

private fun Char.hasCase(): Boolean = isUpperCase() || isLowerCase()

private fun Char.isWordSeparator(): Boolean =
    isWhitespace() || this == '_' || this == '-' || this == ':' || this == '+' || this == '.'

/**
 * Detects if this is a new word start.
 *
 * **Swing equivalent:**:
 * [NameUtilCore.isWordStart](https://github.com/JetBrains/intellij-community/blob/master/platform/util/base/src/com/intellij/util/text/NameUtilCore.java)
 */
private fun CharSequence.isWordStartingAt(index: Int): Boolean {
    val cur = this[index].code
    val prev = if (index > 0) this[index - 1].code else -1
    if (cur.toChar().isUpperCase()) {
        if (prev.toChar().isUpperCase()) {
            // check that we're not in the middle of an all-caps word
            val nextPos = index + 1
            return nextPos < length && this[nextPos].isLowerCase()
        }
        return true
    }
    if (cur.toChar().isDigit()) {
        return true
    }
    if (!cur.toChar().isLetter()) {
        return false
    }
    if (Character.isIdeographic(cur)) {
        // Consider every ideograph as a separate word
        return true
    }
    return index == 0 || !prev.toChar().isLetterOrDigit() || isHardCodedWordStart(index) || cur.isKanaBreakFrom(prev)
}

/**
 * Word detection methods region.
 *
 * **Swing equivalent:**:
 * [NameUtilCore.isHardCodedWordStart](https://github.com/JetBrains/intellij-community/blob/master/platform/util/base/src/com/intellij/util/text/NameUtilCore.java)
 */
private fun CharSequence.isHardCodedWordStart(i: Int): Boolean =
    this[i] == 'l' && i < length - 1 && this[i + 1] == 'n' && (length == i + 2 || isWordStartingAt(i + 2))

/**
 * Gets the next word after a given index.
 *
 * **Swing equivalent:**:
 * [MinusculeMatcherImpl.nextWord](https://github.com/JetBrains/intellij-community/blob/master/platform/util/text-matching/src/com/intellij/psi/codeStyle/MinusculeMatcherImpl.java)
 */
private fun CharSequence.nextWordAfter(start: Int): Int {
    if (start < length && this[start].isDigit()) {
        return start + 1 // treat each digit as a separate hump
    }
    return nextWordAfter(start)
}

/**
 * Check if the string is composed by only ascii characters.
 *
 * **Swing equivalent:**:
 * [AsciiUtils.isAscii](https://github.com/JetBrains/intellij-community/blob/master/platform/util/text-matching/src/com/intellij/psi/codeStyle/AsciiUtils.java)
 */
private fun CharSequence.isAscii(): Boolean = all { it.code < 128 }

/**
 * **Swing equivalent:**:
 * - * [NameUtilCore.KANA_START](https://github.com/JetBrains/intellij-community/blob/master/platform/util/base/src/com/intellij/util/text/NameUtilCore.java)
 * - * [NameUtilCore.KANA_END](https://github.com/JetBrains/intellij-community/blob/master/platform/util/base/src/com/intellij/util/text/NameUtilCore.java)
 * - * [NameUtilCore.KANA2_START](https://github.com/JetBrains/intellij-community/blob/master/platform/util/base/src/com/intellij/util/text/NameUtilCore.java)
 * - * [NameUtilCore.KANA2_END](https://github.com/JetBrains/intellij-community/blob/master/platform/util/base/src/com/intellij/util/text/NameUtilCore.java)
 */
private val KANA_RANGE = 0x3040..0x3358
private val KANA2_RANGE = 0xFF66..0xFF9D

/**
 * **Swing equivalent:**:
 * [NameUtilCore.maybeKana](https://github.com/JetBrains/intellij-community/blob/master/platform/util/base/src/com/intellij/util/text/NameUtilCore.java)
 */
private fun Int.maybeKana(): Boolean = this in KANA_RANGE || this in KANA2_RANGE

/**
 * **Swing equivalent:**:
 * [NameUtilCore.isKanaBreak](https://github.com/JetBrains/intellij-community/blob/master/platform/util/base/src/com/intellij/util/text/NameUtilCore.java)
 */
private fun Int.isKanaBreakFrom(prev: Int): Boolean {
    if (!maybeKana() && !prev.maybeKana()) return false
    val curScript = Character.UnicodeScript.of(this)
    val prevScript = Character.UnicodeScript.of(prev)
    if (prevScript == curScript) return false
    return (curScript == Character.UnicodeScript.KATAKANA ||
        curScript == Character.UnicodeScript.HIRAGANA ||
        prevScript == Character.UnicodeScript.KATAKANA ||
        prevScript == Character.UnicodeScript.HIRAGANA) &&
        prevScript != Character.UnicodeScript.COMMON &&
        curScript != Character.UnicodeScript.COMMON
}

/**
 * Camel-hump matching is >O(n), so for larger prefixes we fall back to simpler matching to avoid pauses
 *
 * **Swing equivalent:**:
 * [MinusculeMatcherImpl.MAX_CAMEL_HUMP_MATCHING_LENGTH](https://github.com/JetBrains/intellij-community/blob/master/platform/util/text-matching/src/com/intellij/psi/codeStyle/MinusculeMatcherImpl.java)
 */
private const val MAX_CAMEL_HUMP_MATCHING_LENGTH = 100
