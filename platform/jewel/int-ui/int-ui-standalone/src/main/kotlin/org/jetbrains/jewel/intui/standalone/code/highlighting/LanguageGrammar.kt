// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jewel.intui.standalone.code.highlighting

import androidx.compose.runtime.Immutable
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.jewel.foundation.ExperimentalJewelApi

/**
 * Defines the syntax highlighting grammar for a single programming language.
 *
 * A grammar consists of a canonical [name] and optional [aliases] (all lowercase) that identify the language in a
 * Markdown fenced code block info string, and an ordered list of [TokenRule]s that are applied to tokenize the source
 * code.
 *
 * Rules are tried at each position in the input; the rule whose match starts earliest wins. In case of a tie, the rule
 * that appears first in [rules] wins, so higher-priority rules (e.g., comments and strings) should come first.
 *
 * @param name Canonical name of the language (e.g, `"kotlin"`). Matched against the info string of a Markdown fenced
 *   code block, along with [aliases].
 * @param aliases Alternative identifiers for the language, typically file extensions or shorthand (e.g, `["kt",
 *   "kts"]`).
 * @param rules Priority-ordered list of [TokenRule]s. Each rule's [TokenRule.captures] maps capture group indices to
 *   [TokenType]s. A single rule can color multiple parts of a match independently.
 */
@ApiStatus.Experimental
@ExperimentalJewelApi
@Immutable
public class LanguageGrammar(public val name: String, aliases: List<String> = emptyList(), rules: List<TokenRule>) {
    public val aliases: List<String> = aliases.toList()
    public val rules: List<TokenRule> = rules.toList()

    private val allIdentifiers: Set<String> = buildSet {
        add(name)
        addAll(this@LanguageGrammar.aliases)
    }

    public fun matches(languageTag: String): Boolean = languageTag in allIdentifiers

    public companion object
}
