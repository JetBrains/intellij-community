// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jewel.intui.standalone.code.highlighting

import androidx.compose.runtime.Immutable
import org.intellij.lang.annotations.Language
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.jewel.foundation.ExperimentalJewelApi

/**
 * A single syntax highlighting rule, consisting of a regex [pattern] and a map from capture group indices to
 * [TokenType]s.
 *
 * Rules are applied in order. The tokenizer picks the rule whose match starts earliest in the remaining input; in case
 * of a tie, the rule that appears first in the list wins.
 *
 * The [captures] map controls which parts of the match are colored:
 * - Key `0` refers to the entire match.
 * - Keys `1`, `2`, etc. refer to specific capturing groups.
 *
 * A single rule can produce multiple spans — for example, a rule that matches `fun myFunc` can color `fun` as
 * [TokenType.KEYWORD] (group 1) and `myFunc` as [TokenType.FUNCTION_CALL] (group 2) in one pass.
 *
 * Patterns use Java's `java.util.regex` engine, which supports lookahead (`(?=...)`) and fixed-width lookbehind
 * (`(?<=...)`, `(?<!...)`). This covers the vast majority of tmLanguage-style patterns.
 *
 * **Known limitations compared to PCRE/Oniguruma** (used by TextMate grammars):
 * - **Possessive quantifiers** (`*+`, `++`, `?+`) are not supported — replace with greedy equivalents or atomic groups
 *   where needed.
 * - **POSIX character classes** (`[[:alpha:]]`, `[[:digit:]]`, etc.) are not supported — replace with their Unicode
 *   equivalents (`[a-zA-Z]`, `[0-9]`, etc.).
 * - **Variable-length lookbehind** is not supported — only fixed-width lookbehind (e.g., `(?<=fun )` but not
 *   `(?<=fun\s+)`). Rewrite as a capturing-group rule instead.
 * - **Named backreferences** and **conditional patterns** (`(?(condition)yes|no)`) are not supported.
 * - **Subroutine calls** (`\g<name>`) and **recursive patterns** are not supported.
 *
 * @param pattern The regex pattern string used to match against the source code.
 * @param captures A map from capture group index to [TokenType]. Groups not listed here produce no colored span.
 */
@ApiStatus.Experimental
@ExperimentalJewelApi
@Immutable
public class TokenRule(@Language("RegExp") public val pattern: String, public val captures: Map<Int, TokenType>) {
    private val regex: Regex = Regex(pattern)

    internal fun find(input: String, startIndex: Int) = regex.find(input, startIndex)

    public companion object {
        /** Colors the entire match as [TokenType.COMMENT]. */
        public fun comment(@Language("RegExp") pattern: String): TokenRule =
            TokenRule(pattern, mapOf(0 to TokenType.COMMENT))

        /** Colors the entire match as [TokenType.STRING]. */
        public fun string(@Language("RegExp") pattern: String): TokenRule =
            TokenRule(pattern, mapOf(0 to TokenType.STRING))

        /** Colors the entire match as [TokenType.KEYWORD]. */
        public fun keyword(@Language("RegExp") pattern: String): TokenRule =
            TokenRule(pattern, mapOf(0 to TokenType.KEYWORD))

        /** Colors the entire match as [TokenType.TYPE]. */
        public fun type(@Language("RegExp") pattern: String): TokenRule = TokenRule(pattern, mapOf(0 to TokenType.TYPE))

        /** Colors the entire match as [TokenType.CONSTANT]. */
        public fun constant(@Language("RegExp") pattern: String): TokenRule =
            TokenRule(pattern, mapOf(0 to TokenType.CONSTANT))

        /** Colors the entire match as [TokenType.NUMBER]. */
        public fun number(@Language("RegExp") pattern: String): TokenRule =
            TokenRule(pattern, mapOf(0 to TokenType.NUMBER))

        /** Colors the entire match as [TokenType.BUILTIN]. */
        public fun builtin(@Language("RegExp") pattern: String): TokenRule =
            TokenRule(pattern, mapOf(0 to TokenType.BUILTIN))

        /**
         * Colors **group 1** of the match as [TokenType.FUNCTION_CALL].
         *
         * Use a capturing group to isolate the function name, excluding any surrounding context such as trailing
         * whitespace before `(`. Example: `\b([A-Za-z_]\w*)\s*(?=\()` — group 1 captures only the name.
         */
        public fun functionCall(@Language("RegExp") pattern: String): TokenRule =
            TokenRule(pattern, mapOf(1 to TokenType.FUNCTION_CALL))

        /**
         * Colors **group 1** as [TokenType.KEYWORD] and **group 2** as [TokenType.FUNCTION_CALL] in a single match.
         *
         * Use this for languages that declare functions with an explicit keyword, e.g. `fun myFunc` in Kotlin or `func
         * myFunc` in Go. Group 1 should capture the keyword, group 2 the function name.
         */
        public fun functionDeclaration(@Language("RegExp") pattern: String): TokenRule =
            TokenRule(pattern, mapOf(1 to TokenType.KEYWORD, 2 to TokenType.FUNCTION_CALL))

        /**
         * Colors **group 1** as [TokenType.KEYWORD] and **group 2** as [TokenType.BUILTIN] in a single match.
         *
         * Use this for type declaration keywords followed by the type name, e.g. `class MyClass` or `interface Foo`.
         * Group 1 should capture the keyword, group 2 the type name.
         */
        public fun typeDeclaration(@Language("RegExp") pattern: String): TokenRule =
            TokenRule(pattern, mapOf(1 to TokenType.KEYWORD, 2 to TokenType.BUILTIN))
    }
}
