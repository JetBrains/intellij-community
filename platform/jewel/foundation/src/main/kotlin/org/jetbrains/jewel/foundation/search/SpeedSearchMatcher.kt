// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jewel.foundation.search

import org.jetbrains.annotations.ApiStatus
import org.jetbrains.jewel.foundation.GenerateDataFunctions
import org.jetbrains.jewel.foundation.InternalJewelApi
import org.jetbrains.jewel.foundation.search.impl.ExactSubstringSpeedSearchMatcher
import org.jetbrains.jewel.foundation.search.impl.PatternSpeedSearchMatcher

public fun interface SpeedSearchMatcher {
    /**
     * Returns a [MatchResult.Match] with a list of ranges from the where the pattern matches, or [MatchResult.NoMatch]
     * if the pattern does not match.
     */
    public fun matches(text: String?): MatchResult

    /**
     * Returns a [MatchResult.Match] with a list of ranges from the where the pattern matches, or [MatchResult.NoMatch]
     * if the pattern does not match.
     */
    public fun matches(text: CharSequence?): MatchResult = matches(text?.toString())

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
 * A [SpeedSearchMatcher] implementation that never matches any text.
 *
 * This matcher is used internally as a performance optimization when the search pattern is empty or invalid. Instead of
 * creating a full matcher that would never match anything, this singleton provides a consistent [MatchResult.NoMatch]
 * response for all inputs.
 *
 * **Internal API:** This object is automatically used by `SpeedSearchState` when the filter text is empty. Users should
 * not instantiate or use this matcher directly. Instead, use [SpeedSearchMatcher.patternMatcher] or
 * [SpeedSearchMatcher.exactSubstringMatcher] to create matchers with actual patterns.
 *
 * @see SpeedSearchMatcher for creating matchers with actual patterns
 * @see filter for filtering collections that handle this matcher efficiently
 */
@InternalJewelApi
@ApiStatus.Internal
public object EmptySpeedSearchMatcher : SpeedSearchMatcher {

    override fun matches(text: String?): SpeedSearchMatcher.MatchResult = SpeedSearchMatcher.MatchResult.NoMatch
}

/**
 * Checks whether the given text matches the current [SpeedSearchMatcher] pattern.
 *
 * This is a convenience method that simplifies checking for matches by returning a boolean instead of requiring pattern
 * matching on [SpeedSearchMatcher.MatchResult].
 *
 * Example:
 * ```kotlin
 * val matcher = SpeedSearchMatcher.patternMatcher("foo")
 * matcher.doesMatch("foobar") // true
 * matcher.doesMatch("baz") // false
 * ```
 *
 * @param text The text to check for matches. If null, returns false.
 * @return `true` if the text matches the pattern, `false` otherwise.
 * @see SpeedSearchMatcher.matches for the underlying match result with ranges
 */
public fun CharSequence.matches(matcher: SpeedSearchMatcher): Boolean =
    matcher.matches(this) != SpeedSearchMatcher.MatchResult.NoMatch

/**
 * Filters an iterable collection based on whether items match the given [SpeedSearchMatcher].
 *
 * For each item in the collection, the [textRepresentationProvider] function is used to extract a string
 * representation, which is then matched against the [matcher]. Items that match are included in the returned list.
 *
 * If the [matcher] is [EmptySpeedSearchMatcher], all items are returned without filtering, optimizing the common case
 * of an empty search query.
 *
 * Example:
 * ```kotlin
 * data class User(val name: String, val email: String)
 * val users = listOf(
 *     User("John Doe", "john@example.com"),
 *     User("Jane Smith", "jane@example.com")
 * )
 *
 * val matcher = SpeedSearchMatcher.patternMatcher("john")
 * val filtered = users.filter(matcher) { it.name }
 * // Returns: [User("John Doe", "john@example.com")]
 * ```
 *
 * @param T The type of items in the collection.
 * @param matcher The [SpeedSearchMatcher] to use for filtering.
 * @param textRepresentationProvider A function that extracts a string representation from each item for matching.
 * @return A list containing only the items that match the search pattern.
 * @see matches for the underlying boolean match check
 */
public fun <T> Iterable<T>.filter(
    matcher: SpeedSearchMatcher,
    textRepresentationProvider: (T) -> CharSequence,
): List<T> =
    if (matcher is EmptySpeedSearchMatcher) {
        toList()
    } else {
        filter { textRepresentationProvider(it).matches(matcher) }
    }

/**
 * Filters an iterable collection of strings based on the given [SpeedSearchMatcher].
 *
 * This is a convenience overload of [filter] for working directly with string collections, eliminating the need to
 * provide a string extraction function.
 *
 * Example:
 * ```kotlin
 * val frameworks = listOf("React", "Vue.js", "Angular", "Svelte")
 * val matcher = SpeedSearchMatcher.patternMatcher("react")
 * val filtered = frameworks.filter(matcher)
 * // Returns: ["React"]
 * ```
 *
 * @param matcher The [SpeedSearchMatcher] to use for filtering.
 * @return A list containing only the strings that match the search pattern.
 * @see filter for filtering collections of other types
 */
public fun <T : CharSequence> Iterable<T>.filter(matcher: SpeedSearchMatcher): List<T> = filter(matcher) { it }

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
