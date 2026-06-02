// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jewel.intui.standalone.code.highlighting.languages

import org.jetbrains.jewel.intui.standalone.code.highlighting.LanguageGrammar
import org.jetbrains.jewel.intui.standalone.code.highlighting.TokenRule

// Patterns adapted from plugins/textmate/lib/bundles/kotlin/syntaxes/kotlin.tmLanguage.json
internal val KOTLIN =
    LanguageGrammar(
        names = listOf("kotlin", "kt", "kts"),
        rules =
            listOf(
                // Comments must come first to avoid matching inside them
                TokenRule.comment("/\\*[\\s\\S]*?\\*/"),
                TokenRule.comment("//[^\n]*"),
                // Strings
                TokenRule.string("\"\"\"[\\s\\S]*?\"\"\""),
                TokenRule.string("\"(?:[^\"\\\\]|\\\\.)*\""),
                TokenRule.string("'(?:[^'\\\\]|\\\\.)*'"),
                // fun <name> — entity.name.function.kotlin
                TokenRule.functionDeclaration("\\b(fun)\\s+([A-Za-z_][A-Za-z0-9_]*)"),
                // class/interface/object/enum <name> — entity.name.type.class.kotlin
                TokenRule.typeDeclaration("\\b(class|interface|object|enum)\\s+([A-Za-z_][A-Za-z0-9_]*)"),
                // storage.type + storage.modifier — val/var grouped with modifiers per kotlin.tmLanguage.json
                TokenRule.keyword(
                    "\\b(val|var|fun|class|interface|annotation|companion|object|package|import|" +
                        "typealias|this|super|constructor|init|value|override|abstract|final|open|enum|sealed|" +
                        "data|inline|noinline|tailrec|external|const|suspend|expect|actual|private|public|" +
                        "internal|protected|lateinit|vararg|crossinline|operator|infix|reified)\\b"
                ),
                // keyword.control.kotlin
                TokenRule.keyword(
                    "\\b(if|else|when|for|do|while|return|break|continue|throw|try|catch|finally|in|" +
                        "is|as|by|get|set|where)\\b"
                ),
                // entity.name.function.kotlin — call sites (after keywords, so if/when/for/while/catch won't match)
                TokenRule.functionCall("\\b([A-Za-z_][A-Za-z0-9_]*)\\s*(?=\\()"),
                // constant.language.kotlin
                TokenRule.constant("\\b(true|false|null)\\b"),
                // support.type.kotlin
                TokenRule.type(
                    "\\b(String|Int|Long|Double|Float|Boolean|Char|Byte|Short|Unit|Any|Nothing|Array|" +
                        "List|MutableList|Map|MutableMap|Set|MutableSet|Pair)\\b"
                ),
                // constant.numeric.kotlin
                // Suffix pattern: uL/UL/ul/Ul for ULong, u/U for UInt, L/l for Long, f/F for Float
                TokenRule.number("\\b0[bB][01_]+([uU][lL]?|[lL])?\\b"),
                TokenRule.number("\\b0[xX][0-9a-fA-F_]+([uU][lL]?|[lL])?\\b"),
                TokenRule.number("\\b[0-9][0-9_]*(?:\\.[0-9_]+)?(?:[eE][+-]?[0-9]+)?(?:[uU][lL]?|[lL]|[fF])?\\b"),
            ),
    )
