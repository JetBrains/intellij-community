// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jewel.intui.standalone.code.highlighting.languages

import org.jetbrains.jewel.intui.standalone.code.highlighting.LanguageGrammar
import org.jetbrains.jewel.intui.standalone.code.highlighting.TokenRule

// Patterns adapted from plugins/textmate/lib/bundles/java/syntaxes/java.tmLanguage.json
internal val JAVA =
    LanguageGrammar(
        name = "java",
        aliases = listOf("jav", "jsh"),
        rules =
            listOf(
                // Comments must come first
                TokenRule.comment("/\\*[\\s\\S]*?\\*/"),
                TokenRule.comment("//[^\n]*"),
                // Strings (text blocks first — Java 13+)
                TokenRule.string("\"\"\"[\\s\\S]*?\"\"\""),
                TokenRule.string("\"(?:[^\"\\\\]|\\\\.)*\""),
                TokenRule.string("'(?:[^'\\\\]|\\\\.)*'"),
                // keyword.control + storage.modifier.java + storage.type.java
                TokenRule.keyword(
                    "\\b(abstract|assert|break|case|catch|class|const|continue|default|do|else|enum|" +
                        "extends|final|finally|for|goto|if|implements|import|instanceof|interface|native|new|" +
                        "package|private|protected|public|record|return|sealed|static|strictfp|super|switch|" +
                        "synchronized|this|throw|throws|transient|try|var|volatile|while|permits)\\b"
                ),
                // constant.language.java
                TokenRule.constant("\\b(true|false|null)\\b"),
                // storage.type.primitive.java
                TokenRule.type("\\b(boolean|byte|char|double|float|int|long|short|void)\\b"),
                // entity.name.function.java — identifier immediately before (
                TokenRule.functionCall("\\b([A-Za-z_][A-Za-z0-9_]*)\\s*(?=\\()"),
                // support.class.java — common boxed types and stdlib roots
                TokenRule.builtin(
                    "\\b(String|Object|Integer|Long|Double|Float|Boolean|Character|Byte|Short|Number|" +
                        "Math|System)\\b"
                ),
                // constant.numeric.java
                TokenRule.number("\\b0[xX][0-9a-fA-F_]+[Ll]?\\b"),
                TokenRule.number("\\b0[bB][01_]+[Ll]?\\b"),
                TokenRule.number("\\b[0-9][0-9_]*(?:\\.[0-9_]+)?(?:[eE][+-]?[0-9]+)?[LlFfDd]?\\b"),
            ),
    )
