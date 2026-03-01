// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jewel.foundation.search

import org.jetbrains.jewel.foundation.GenerateDataFunctions
import org.jetbrains.jewel.foundation.search.SpeedSearchMatcher.Companion.patternMatcher
import org.jetbrains.jewel.foundation.search.impl.ExactSubstringSpeedSearchMatcher
import org.jetbrains.jewel.foundation.search.impl.PatternSpeedSearchMatcher

public fun interface SpeedSearchMatcher {
    /**
     * Returns a [MatchResult.Match] with a list of ranges from the where the pattern matches, or [MatchResult.NoMatch]
     * if the pattern does not match.
     */
    public fun matches(text: String?): MatchResult

    public companion object {
        /**
         * Returns a [SpeedSearchMatcher] that searches for the given [pattern] in the text.
         *
         * Notes:
         * - This method only matches if the [pattern] is a substring of the text.
         * - This method returns only the first occurrence of the pattern.
         *
         * Examples:
         * ```kotlin
         * val matcher = SpeedSearchMatcher.exactSubstringMatcher("foo")
         * matcher.matches("foobar") // [0..3]
         * matcher.matches("foboar") // null
         *
         * val matcher = SpeedSearchMatcher.exactSubstringMatcher("eye")
         * val result = matcher.matches("an eye for an eye") // [3..6]
         * ```
         *
         * If you need a more flexible way to match text, use [patternMatcher].
         *
         * @param pattern The string to search for. Must be a non-empty string.
         * @param ignoreCase Whether to ignore the case of the pattern. Defaults to `true`.
         */
        public fun exactSubstringMatcher(pattern: String, ignoreCase: Boolean = true): SpeedSearchMatcher =
            ExactSubstringSpeedSearchMatcher(pattern, ignoreCase)

        /**
         * Returns a [SpeedSearchMatcher] that searches for the given [pattern] in the text. This pattern can match
         * multiple parts of the string submitted in the [SpeedSearchMatcher.matches] function, not needing the whole
         * string to match as one substring.
         *
         * Examples:
         * ```kotlin
         * val matcher = SpeedSearchMatcher.patternMatcher("foo")
         * matcher.matches("foobar") // [0..3]
         * matcher.matches("foboar") // [0..2, 3..4]
         *
         * val matcher = SpeedSearchMatcher.patternMatcher("eye")
         * val result = matcher.matches("an eye for an eye") // [3..6]
         * ```
         *
         * @param pattern The pattern to search for. Must be a non-empty string.
         * @param matchFromBeginning Whether to match the pattern must start from the beginning of the string. Defaults
         *   to `false` to match patterns that start anywhere in the string.
         * @param caseSensitivity The case sensitivity handling during a pattern match. Defaults to
         *   [MatchingCaseSensitivity.None].
         * @param ignoredSeparators A string containing characters that should not be considered separators. By default,
         *   all special characters are considered separators.
         */
        public fun patternMatcher(
            pattern: String,
            matchFromBeginning: Boolean = false,
            caseSensitivity: MatchingCaseSensitivity = MatchingCaseSensitivity.None,
            ignoredSeparators: String = "",
        ): SpeedSearchMatcher =
            PatternSpeedSearchMatcher(
                basePattern = pattern.convertToPattern(matchFromBeginning),
                options = caseSensitivity,
                ignoredSeparators = ignoredSeparators,
                containsMatcher = exactSubstringMatcher(pattern, caseSensitivity != MatchingCaseSensitivity.All),
            )
    }

    public sealed interface MatchResult {
        public object NoMatch : MatchResult

        @GenerateDataFunctions
        public class Match(public val ranges: List<IntRange>) : MatchResult {
            override fun equals(other: Any?): Boolean {
                if (this === other) return true
                if (javaClass != other?.javaClass) return false

                other as Match

                return ranges == other.ranges
            }

            override fun hashCode(): Int = ranges.hashCode()

            override fun toString(): String = "Match(ranges=$ranges)"
        }

        public companion object {
            internal fun from(ranges: List<IntRange>?): MatchResult =
                if (ranges.isNullOrEmpty()) NoMatch else Match(ranges)
        }
    }
}

/**
 * Case sensitivity options for matching.
 *
 * **Swing equivalent:**
 * [NameUtil.MatchingCaseSensitivity](https://github.com/JetBrains/intellij-community/blob/master/platform/util/text-matching/src/com/intellij/psi/codeStyle/NameUtil.java)
 */
public enum class MatchingCaseSensitivity {
    /** When set, it's not required to match the case for the pattern. */
    None,

    /**
     * When set, The first letter from a pattern block must match the case.
     *
     * Examples:
     * ```kotlin
     * val matcher = SpeedSearchMatcher.patternMatcher("AbCd", caseSensitivity = MatchingCaseSensitivity.FirstLetter)
     *
     * // Match exact case
     * matcher.matches("AbCdef") // [0..4]
     *
     * // Matching upper case with lower case from pattern, if that's not the first letter
     * matcher.matches("ABCdef") // [0..4]
     * matcher.matches("AbCDef") // [0..4]
     * matcher.matches("ABCDef") // [0..4]
     *
     * // Does not match if any upper case from pattern is not in the text
     * matcher.matches("abCdef") // null
     * matcher.matches("Abcdef") // null
     * matcher.matches("abcdef") // null
     * ```
     */
    FirstLetter,

    /**
     * All cases must match for the pattern to match.
     *
     * Examples:
     * ```kotlin
     * val matcher = SpeedSearchMatcher.patternMatcher("AnEyeForAnEye", caseSensitivity = MatchingCaseSensitivity.All)
     *
     * // Match exact case
     * matcher.matches("An Eye For An Eye") // [0..2, 3..6, 7..10, 11..13, 14..17]
     *
     * // Does not match case
     * matcher.matches("AN EYE FOR AN EYE") // null
     * matcher.matches("an eye for an eye") // null
     * matcher.matches("AN Eye For An Eye") // null
     * ```
     */
    All,
}

/**
 * Split the input into words based on case changes, digits, and special characters, and join them with the wildcard
 * ('*') character.
 *
 * **Swing equivalent:**:
 * [SpeedSearchComparator.obtainMatcher](https://github.com/JetBrains/intellij-community/blob/master/platform/platform-impl/src/com/intellij/ui/SpeedSearchComparator.java)
 */
internal fun String.convertToPattern(matchFromBeginning: Boolean): String {
    if (isBlank()) return this

    val buildPattern =
        buildList {
                var index = 0

                while (index < length) {
                    val wordStart = index
                    var upperCaseCount = 0
                    var lowerCaseCount = 0
                    var digitCount = 0
                    var specialCount = 0

                    @Suppress("LoopWithTooManyJumpStatements")
                    while (index < length) {
                        val c = this@convertToPattern[index]
                        when {
                            c.isDigit() -> {
                                if (upperCaseCount > 0 || lowerCaseCount > 0 || specialCount > 0) break
                                digitCount++
                            }
                            c.isUpperCase() -> {
                                if (lowerCaseCount > 0 || digitCount > 0 || specialCount > 0) break
                                if (
                                    upperCaseCount > 1 &&
                                        index + 1 < length &&
                                        this@convertToPattern[index + 1].isLowerCase()
                                ) {
                                    index--
                                    break
                                }
                                upperCaseCount++
                            }
                            c.isLowerCase() -> {
                                if (digitCount > 0 || specialCount > 0) break
                                lowerCaseCount++
                            }
                            else -> {
                                if (upperCaseCount > 0 || lowerCaseCount > 0 || digitCount > 0) break
                                specialCount++
                            }
                        }
                        index++
                    }

                    val word = substring(wordStart, index)
                    if (word.isNotBlank()) {
                        add(word)
                    }
                }
            }
            .joinToString("*")

    return if (!matchFromBeginning && !buildPattern.startsWith("*")) {
        "*$buildPattern"
    } else {
        buildPattern
    }
}
