// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jewel.intui.standalone.code.highlighting

import androidx.compose.runtime.Immutable
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.jewel.foundation.ExperimentalJewelApi

/**
 * Defines the syntax highlighting grammar for a single programming language.
 *
 * A grammar consists of a set of [names] (all lowercase) that identify the language in a Markdown fenced code block
 * info string, and an ordered list of [TokenRule]s that are applied to tokenize the source code.
 *
 * Rules are tried at each position in the input; the rule whose match starts earliest wins. In case of a tie, the rule
 * that appears first in [rules] wins, so higher-priority rules (e.g., comments and strings) should come first.
 *
 * @param names All lowercase names and aliases for this language (e.g., `["kotlin", "kt", "kts"]`). These are matched
 *   against the info string of a Markdown fenced code block.
 * @param rules Priority-ordered list of [TokenRule]s. Each rule's [TokenRule.captures] maps capture group indices to
 *   [TokenType]s. A single rule can color multiple parts of a match independently.
 */
@ApiStatus.Experimental
@ExperimentalJewelApi
@Immutable
public class LanguageGrammar(public val names: List<String>, public val rules: List<TokenRule>) {
    public companion object
}
