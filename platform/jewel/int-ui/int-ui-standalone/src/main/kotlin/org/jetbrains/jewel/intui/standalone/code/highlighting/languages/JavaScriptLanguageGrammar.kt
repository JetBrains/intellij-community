// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jewel.intui.standalone.code.highlighting.languages

import org.jetbrains.jewel.intui.standalone.code.highlighting.LanguageGrammar
import org.jetbrains.jewel.intui.standalone.code.highlighting.TokenRule
import org.jetbrains.jewel.intui.standalone.code.highlighting.TokenType

// Patterns adapted from plugins/textmate/lib/bundles/javascript/syntaxes/JavaScript.tmLanguage.json
// Aliases from https://github.com/github-linguist/linguist/blob/main/lib/linguist/languages.yml
//
// `$` is an identifier character in JS, so word boundaries are written as (?<![\w$]) / (?![\w$]) rather
// than \b — otherwise `$in`, `$of` and friends (common in query DSLs) get colored as keywords.
internal val JAVASCRIPT =
    LanguageGrammar(
        name = "javascript",
        aliases =
            listOf(
                "js",
                "node",
                "_js",
                "bones",
                "cjs",
                "es",
                "es6",
                "frag",
                "gs",
                "jake",
                "jsb",
                "jscad",
                "jsfl",
                "jslib",
                "jsm",
                "jspre",
                "jss",
                "mjs",
                "njs",
                "pac",
                "sjs",
                "ssjs",
                "xsjs",
                "xsjslib",
            ),
        rules =
            listOf(
                // Comments must come first to avoid matching inside them
                // comment.line.shebang.js — \A only matches at the very start of the input
                TokenRule.comment("\\A#!.*"),
                // comment.block.js + comment.block.documentation.js — both map to COMMENT here
                TokenRule.comment("/\\*[\\s\\S]*?\\*/"),
                // comment.line.double-slash.js
                TokenRule.comment("//[^\n]*"),
                // Strings — template literals first. Interpolations are part of the string span.
                TokenRule.string("`(?:[^`\\\\]|\\\\.)*`"),
                TokenRule.string("\"(?:[^\"\\\\]|\\\\.)*\""),
                TokenRule.string("'(?:[^'\\\\]|\\\\.)*'"),
                // meta.object-literal.key.js — anchored to `{` or `,` so ternaries (`a ? b : c`) don't match.
                // Group 2 is the key; the anchor and the colon stay uncolored.
                TokenRule("([{,])\\s*([_\$a-zA-Z][\\w\$]*)\\s*(?=:)", mapOf(2 to TokenType.PROPERTY_KEY)),
                // storage.type.function.js + entity.name.function.js
                TokenRule.functionDeclaration("(?<![\\w\$])(function)\\s+([_\$a-zA-Z][\\w\$]*)"),
                // storage.type.class.js + entity.name.type.js
                TokenRule.typeDeclaration("(?<![\\w\$])(class)\\s+([_\$a-zA-Z][\\w\$]*)"),
                // storage.type.js + storage.modifier.js. `new` and `import` carry keyword.control in one
                // place and keyword.operator.expression in another, which needs the tree to tell apart, so
                // they stay keywords.
                TokenRule.keyword(
                    "(?<![\\w\$])(?:var|let|const|function|class|static|get|set|async|await|yield|new|" +
                        "import|export|from|as|this|super)(?![\\w\$])"
                ),
                // keyword.operator.expression.* — the bundle scopes these as operators, not keywords
                TokenRule.operator("(?<![\\w\$])(?:extends|delete|typeof|instanceof|void|in|of)(?![\\w\$])"),
                // keyword.control.* — flow
                TokenRule.keyword(
                    "(?<![\\w\$])(?:if|else|for|while|do|break|continue|switch|case|default|return|try|catch|" +
                        "finally|throw|with|debugger)(?![\\w\$])"
                ),
                // constant.language.js
                TokenRule.constant("(?<![\\w\$])(?:true|false|null|undefined|NaN|Infinity)(?![\\w\$])"),
                // support.class.* + variable.language.* — well-known globals, before the SCREAMING_CASE rule
                // so all-caps names like JSON stay builtins
                TokenRule.builtin(
                    "(?<![\\w\$])(?:globalThis|arguments|console|Math|JSON|Promise|Object|Array|String|Number|" +
                        "Boolean|Symbol|BigInt|Map|Set|WeakMap|WeakSet|Date|RegExp|Error|Function)(?![\\w\$])"
                ),
                // variable.other.constant.js — SCREAMING_CASE. The trailing lookahead is what stops it from
                // matching the leading `M` of `Math`.
                TokenRule.constant("(?<![\\w\$])[A-Z][A-Z0-9_\$]*(?![\\w\$])"),
                // entity.name.function.js — call sites, after keywords so `if (` stays a keyword
                TokenRule.functionCall("([_\$a-zA-Z][\\w\$]*)\\s*(?=\\()"),
                // constant.numeric.js — the optional `n` suffix is BigInt
                TokenRule.number("(?<![\\w\$])0[bB][01][01_]*n?(?![\\w\$])"),
                TokenRule.number("(?<![\\w\$])0[xX][0-9a-fA-F][0-9a-fA-F_]*n?(?![\\w\$])"),
                TokenRule.number("(?<![\\w\$])0[oO][0-7][0-7_]*n?(?![\\w\$])"),
                TokenRule.number(
                    "(?<![\\w\$])(?:\\d[\\d_]*(?:\\.[\\d_]*)?|\\.\\d[\\d_]*)(?:[eE][+-]?\\d+)?n?(?![\\w\$])"
                ),
            ),
    )
