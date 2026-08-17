// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jewel.intui.standalone.code.highlighting.languages

import org.jetbrains.jewel.intui.standalone.code.highlighting.LanguageGrammar
import org.jetbrains.jewel.intui.standalone.code.highlighting.TokenRule
import org.jetbrains.jewel.intui.standalone.code.highlighting.TokenType

// Patterns adapted from plugins/textmate/lib/bundles/javascript/syntaxes/JavaScriptReact.tmLanguage.json
//
// That bundle is the entire JavaScript grammar re-scoped to `*.js.jsx` plus a handful of tag rules, so this
// grammar layers only the tag rules on top of JAVASCRIPT.rules rather than duplicating them. JSX rules come
// first so they win ties: in `<label for="x">`, `for` is an attribute, not the JS keyword.
private val JSX_RULES =
    listOf(
        // support.class.component.js.jsx
        // capitalized names are components, lowercase ones are DOM elements.
        TokenRule("</?([A-Z][\\w\$.]*)(?=[\\s/>])", mapOf(1 to TokenType.TYPE)),
        // entity.name.tag.js.jsx
        TokenRule("</?([a-z][\\w.:-]*)(?=[\\s/>])", mapOf(1 to TokenType.KEYWORD)),
        // entity.other.attribute-name.js.jsx. The lookbehind is ours, standing in for the opening-tag
        // context the bundle gets from the tree: an unclosed `<` in expression position (so a comparison
        // operator does not qualify), a tag name, then whitespace right before the attribute.
        TokenRule.propertyKey("(?<=(?<![\\w\$)\\]])<[a-zA-Z][^<>]*\\s)[A-Za-z_\$][\\w\$:.-]*(?==[\"'{])"),
        // constant.character.entity.js.jsx
        TokenRule.constant("&(?:[a-zA-Z][a-zA-Z0-9]*|#[0-9]+|#[xX][0-9a-fA-F]+);"),
    )

internal val JSX =
    LanguageGrammar(name = "jsx", aliases = listOf("javascriptreact"), rules = JSX_RULES + JAVASCRIPT.rules)
