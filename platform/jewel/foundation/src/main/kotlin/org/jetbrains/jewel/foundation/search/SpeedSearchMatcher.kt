// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jewel.foundation.search

import org.jetbrains.jewel.foundation.search.impl.ExactSubstringSpeedSearchMatcher
import org.jetbrains.jewel.foundation.search.impl.PatternSpeedSearchMatcher

public fun interface SpeedSearchMatcher {
    public fun matches(text: String?): List<IntRange>?

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
         */
        public fun patternMatcher(
            pattern: String,
            matchFromBeginning: Boolean = false,
            caseSensitivity: MatchingCaseSensitivity = MatchingCaseSensitivity.None,
            hardSeparators: String = "",
        ): SpeedSearchMatcher =
            PatternSpeedSearchMatcher(
                basePattern = pattern.convertToPattern(matchFromBeginning),
                options = caseSensitivity,
                hardSeparators = hardSeparators,
                containsMatcher = exactSubstringMatcher(pattern, caseSensitivity != MatchingCaseSensitivity.All),
            )
    }
}

/**
 * Case sensitivity options for matching.
 *
 * **Swing equivalent:**
 * [NameUtil.MatchingCaseSensitivity](https://github.com/JetBrains/intellij-community/blob/master/platform/util/text-matching/src/com/intellij/psi/codeStyle/NameUtil.java)
 */
public enum class MatchingCaseSensitivity {
    None,
    FirstLetter,
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
