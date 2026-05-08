// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jewel.intui.standalone.code.highlighting

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import kotlin.collections.iterator
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.withContext
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
 * @param highlightDispatcher The [CoroutineDispatcher] on which tokenization is performed. Defaults to
 *   [Dispatchers.Default] so the regex scan runs off the collecting thread (e.g. the composition path).
 */
@ApiStatus.Experimental
@ExperimentalJewelApi
public class SimpleCodeHighlighter(
    public val colors: SyntaxHighlightColors,
    public val additionalGrammars: List<LanguageGrammar>,
    public val highlightDispatcher: CoroutineDispatcher = Dispatchers.Default,
) : CodeHighlighter {
    public constructor(colors: SyntaxHighlightColors) : this(colors, emptyList())

    @Deprecated(message = "Use highlight(code, language) instead.", level = DeprecationLevel.HIDDEN)
    override fun highlight(code: String, mimeType: MimeType?): Flow<AnnotatedString> = flowOf(AnnotatedString(code))

    override fun highlight(code: String, language: String): Flow<AnnotatedString> {
        if (language.isBlank()) return flowOf(AnnotatedString(code))
        val grammar = findGrammar(language) ?: return flowOf(AnnotatedString(code))
        return flow { emit(withContext(highlightDispatcher) { tokenize(code, grammar) }) }
    }

    private fun findGrammar(language: String): LanguageGrammar? {
        val grammars = additionalGrammars + BuiltInLanguageGrammars.all
        val normalizedLanguage = language.trim().lowercase()

        val exactMatch = findGrammar(normalizedLanguage, grammars)
        if (exactMatch != null) {
            return exactMatch
        }

        var index = normalizedLanguage.lastIndexOf(' ')
        while (index != -1) {
            val nameWithoutCustomizations = normalizedLanguage.take(index)
            val grammar = findGrammar(nameWithoutCustomizations, grammars)
            if (grammar != null) {
                return grammar
            }
            index = normalizedLanguage.lastIndexOf(' ', startIndex = (index - 1).coerceAtLeast(0))
        }

        return null
    }

    private fun findGrammar(tag: String, grammars: List<LanguageGrammar>): LanguageGrammar? =
        grammars.firstOrNull { it.matches(tag) }

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
                // if the regex doesn't have as many groups as bestMatch, skip silently
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
