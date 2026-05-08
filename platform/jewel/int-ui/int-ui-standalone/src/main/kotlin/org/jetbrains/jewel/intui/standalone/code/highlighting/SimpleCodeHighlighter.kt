// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jewel.intui.standalone.code.highlighting

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import kotlin.collections.iterator
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.jewel.foundation.ExperimentalJewelApi
import org.jetbrains.jewel.foundation.code.MimeType
import org.jetbrains.jewel.foundation.code.highlighting.CodeHighlighter

/**
 * A lightweight [CodeHighlighter] for use in standalone Jewel applications.
 *
 * Highlighting is driven by a regex rule engine (see [TokenRule]) that mirrors the structure of tmLanguage grammars.
 * Each [LanguageGrammar] contains an ordered list of [TokenRule]s; the tokenizer picks the rule whose match starts
 * earliest in the remaining input (ties broken by rule order), applies its capture group spans, and advances past the
 * match.
 *
 * Built-in grammars are provided by [BuiltInLanguageGrammars]. Custom or additional grammars can be supplied via
 * [additionalGrammars]; these are searched first, so they can override built-in behavior for a given language name.
 *
 * Token colors are controlled by [SyntaxHighlightColors]. Use [SyntaxHighlightColors.Companion.light] and
 * [SyntaxHighlightColors.Companion.dark] for palettes matching IntelliJ's Default and Darcula editor schemes, or supply
 * your own. Theme changes are handled at the call site via `remember(isDark)` in `ProvideMarkdownStyling` — this class
 * itself is stateless and always emits a single [AnnotatedString].
 *
 * @param colors The token color palette to use for styling.
 * @param additionalGrammars Extra grammars to search before the built-in ones. Use this to support additional languages
 *   or to override built-in grammar rules for an existing language.
 */
@ApiStatus.Experimental
@ExperimentalJewelApi
public class SimpleCodeHighlighter(
    public val colors: SyntaxHighlightColors,
    public val additionalGrammars: List<LanguageGrammar>,
) : CodeHighlighter {
    public constructor(colors: SyntaxHighlightColors) : this(colors, emptyList())

    @Deprecated(message = "Use highlight(code, language) instead.", level = DeprecationLevel.HIDDEN)
    override fun highlight(code: String, mimeType: MimeType?): Flow<AnnotatedString> =
        flowOf(AnnotatedString(code))

    override fun highlight(code: String, language: String): Flow<AnnotatedString> {
        if (language.isBlank()) return flowOf(AnnotatedString(code))
        val grammar = findGrammar(language) ?: return flowOf(AnnotatedString(code))
        return flowOf(tokenize(code, grammar))
    }

    private fun findGrammar(language: String): LanguageGrammar? {
        val normalized = language.trim().lowercase()
        return (additionalGrammars + BuiltInLanguageGrammars.all).firstOrNull { normalized in it.names }
    }

    private fun tokenize(code: String, grammar: LanguageGrammar): AnnotatedString {
        data class Span(val start: Int, val end: Int, val style: SpanStyle)

        val spans = mutableListOf<Span>()
        var i = 0

        while (i < code.length) {
            // Find the rule whose match starts earliest; ties broken by rule order
            var bestMatch: MatchResult? = null
            var bestRule: TokenRule? = null

            for (rule in grammar.rules) {
                val match = rule.find(code, i) ?: continue
                if (bestMatch == null || match.range.first < bestMatch.range.first) {
                    bestMatch = match
                    bestRule = rule
                }
            }

            if (bestMatch == null || bestRule == null) break

            for ((groupIndex, tokenType) in bestRule.captures) {
                // if  the regex doesn't have as many groups as bestMach, skip silently
                if (groupIndex >= bestMatch.groupValues.size) continue
                val group = bestMatch.groups[groupIndex] ?: continue
                if (group.range.isEmpty()) continue
                spans += Span(group.range.first, group.range.last + 1, colors.styleFor(tokenType))
            }

            i = maxOf(i + 1, bestMatch.range.last + 1)
        }

        return buildAnnotatedString {
            append(code)
            for (span in spans) addStyle(span.style, span.start, span.end)
        }
    }
}
