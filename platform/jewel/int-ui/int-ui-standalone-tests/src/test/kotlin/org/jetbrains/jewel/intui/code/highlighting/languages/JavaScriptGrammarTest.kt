// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jewel.intui.code.highlighting.languages

import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.jetbrains.jewel.intui.code.highlighting.colorAt
import org.jetbrains.jewel.intui.code.highlighting.spansAt
import org.jetbrains.jewel.intui.code.highlighting.testColors
import org.jetbrains.jewel.intui.standalone.code.highlighting.SimpleCodeHighlighter
import org.junit.jupiter.api.Test

internal class JavaScriptGrammarTest {
    private val highlighter = SimpleCodeHighlighter(testColors)

    private suspend fun highlight(code: String, language: String = "javascript") =
        highlighter.highlight(code, language).first()

    @Test
    fun `name and all aliases are recognized`() = runTest {
        val identifiers =
            listOf(
                "javascript",
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
            )
        for (identifier in identifiers) {
            assertTrue(highlight("let x = 1", identifier).spanStyles.isNotEmpty(), "Alias '$identifier' not recognized")
        }
    }

    @Test
    fun `line comment is colored as comment`() = runTest {
        assertEquals(testColors.comment, highlight("// a comment").colorAt(0))
    }

    @Test
    fun `block comment is colored as comment`() = runTest {
        assertEquals(testColors.comment, highlight("/* block */").colorAt(0))
    }

    @Test
    fun `jsdoc comment is colored as comment`() = runTest {
        assertEquals(testColors.comment, highlight("/** @param x */").colorAt(0))
    }

    @Test
    fun `comments are italic`() = runTest {
        assertEquals(FontStyle.Italic, highlight("// comment").spansAt(0).first().fontStyle)
    }

    @Test
    fun `shebang is colored as comment`() = runTest {
        val result = highlight("#!/usr/bin/env node\nlet x = 1")
        assertEquals(testColors.comment, result.colorAt(0))
        assertEquals(testColors.keyword, result.colorAt(20), "'let' after the shebang should still be a keyword")
    }

    @Test
    fun `shebang only matches at the start of the input`() = runTest {
        assertNull(highlight("let x = 1\n#!/usr/bin/env node").colorAt(10))
    }

    @Test
    fun `keyword inside comment is not colored as keyword`() = runTest {
        val result = highlight("// const let var")
        assertEquals(testColors.comment, result.colorAt(3))
        assertNotEquals(testColors.keyword, result.colorAt(3))
    }

    @Test
    fun `double-quoted string is colored as string`() = runTest {
        assertEquals(testColors.string, highlight("\"hello\"").colorAt(0))
    }

    @Test
    fun `single-quoted string is colored as string`() = runTest {
        assertEquals(testColors.string, highlight("'hello'").colorAt(0))
    }

    @Test
    fun `template literal is colored as string`() = runTest {
        assertEquals(testColors.string, highlight("`hello`").colorAt(0))
    }

    @Test
    fun `keyword inside string is not colored as keyword`() = runTest {
        val result = highlight("\"const x\"")
        assertEquals(testColors.string, result.colorAt(1))
        assertNotEquals(testColors.keyword, result.colorAt(1))
    }

    @Test
    fun `function declaration colors keyword and name separately`() = runTest {
        val result = highlight("function myFunc() {}")
        assertEquals(testColors.keyword, result.colorAt(0)) // "function"
        assertEquals(testColors.functionCall, result.colorAt(9)) // "myFunc"
    }

    @Test
    fun `class declaration colors keyword and name separately`() = runTest {
        val result = highlight("class Foo {}")
        assertEquals(testColors.keyword, result.colorAt(0)) // "class"
        assertEquals(testColors.type, result.colorAt(6)) // "Foo"
    }

    @Test
    fun `declaration keywords are colored as keyword`() = runTest {
        for (keyword in listOf("var", "let", "const", "function", "class", "new", "async", "await")) {
            assertEquals(testColors.keyword, highlight(keyword).colorAt(0), "'$keyword' should be colored as keyword")
        }
        // keyword.operator.expression.* in the bundle, so not the keyword key
        for (word in listOf("typeof", "instanceof", "void", "delete", "in", "of", "extends")) {
            assertEquals(testColors.operator, highlight(word).colorAt(0), "'$word' should be an operator")
        }
    }

    @Test
    fun `control flow keywords are colored as keyword`() = runTest {
        for (keyword in listOf("if", "else", "for", "while", "return", "switch", "case", "try", "catch", "throw")) {
            assertEquals(testColors.keyword, highlight(keyword).colorAt(0), "'$keyword' should be colored as keyword")
        }
    }

    @Test
    fun `keywords are bold`() = runTest {
        assertEquals(FontWeight.Bold, highlight("const").spansAt(0).first().fontWeight)
    }

    @Test
    fun `keywords before parenthesis are not colored as function calls`() = runTest {
        assertEquals(testColors.keyword, highlight("if (x) {}").colorAt(0))
    }

    @Test
    fun `call site is colored as function call`() = runTest {
        // "log" starts at index 8 in "console.log()"
        assertEquals(testColors.functionCall, highlight("console.log()").colorAt(8))
    }

    @Test
    fun `language constants are colored as constant`() = runTest {
        for (constant in listOf("true", "false", "null", "undefined", "NaN", "Infinity")) {
            assertEquals(
                testColors.constant,
                highlight(constant).colorAt(0),
                "'$constant' should be colored as constant",
            )
        }
    }

    @Test
    fun `screaming case identifiers are colored as constant`() = runTest {
        for (constant in listOf("MAX_VALUE", "API_KEY", "X")) {
            assertEquals(
                testColors.constant,
                highlight(constant).colorAt(0),
                "'$constant' should be colored as constant",
            )
        }
    }

    @Test
    fun `well-known globals are colored as builtin`() = runTest {
        for (builtin in listOf("console", "Math", "JSON", "Promise", "Object")) {
            assertEquals(testColors.builtin, highlight(builtin).colorAt(0), "'$builtin' should be colored as builtin")
        }
    }

    @Test
    fun `all-caps globals are builtins rather than screaming case constants`() = runTest {
        assertEquals(testColors.builtin, highlight("JSON").colorAt(0))
        assertNotEquals(testColors.constant, highlight("JSON").colorAt(0))
    }

    @Test
    fun `screaming case rule does not match the leading capital of a mixed-case name`() = runTest {
        // "Math" is a builtin, not a "M" constant followed by "ath"
        assertEquals(testColors.builtin, highlight("Math").colorAt(0))
        assertEquals(testColors.builtin, highlight("Math").colorAt(1))
    }

    @Test
    fun `object literal key is colored as property key`() = runTest {
        assertEquals(testColors.propertyKey, highlight("{a: 1}").colorAt(1))
    }

    @Test
    fun `second object literal key is colored as property key`() = runTest {
        // "b" is at index 7 in "{a: 1, b: 2}" — reached via the `,` anchor
        assertEquals(testColors.propertyKey, highlight("{a: 1, b: 2}").colorAt(7))
    }

    @Test
    fun `ternary is not mistaken for an object literal key`() = runTest {
        // Without the `{`/`,` anchor, "a" in `x ? a : b` would match the key pattern
        assertNull(highlight("x ? a : b").colorAt(4))
    }

    @Test
    fun `object literal braces and colons are not colored`() = runTest {
        val result = highlight("{a: 1}")
        assertNull(result.colorAt(0), "The `{` anchor should not be colored")
        assertNull(result.colorAt(2), "The colon should not be colored")
    }

    @Test
    fun `dollar-prefixed identifiers are not colored as keywords`() = runTest {
        // `$` is an identifier character in JS, so \b would wrongly match "in" here
        assertNull(highlight("\$in").colorAt(1))
        assertNull(highlight("\$of").colorAt(1))
    }

    @Test
    fun `decimal numbers are colored as number`() = runTest {
        for (number in listOf("0", "42", "3.14", ".5", "1e10", "1_000")) {
            assertEquals(testColors.number, highlight(number).colorAt(0), "'$number' should be colored as number")
        }
    }

    @Test
    fun `radix-prefixed numbers are colored as number`() = runTest {
        for (number in listOf("0xFF", "0b1010", "0o777")) {
            assertEquals(testColors.number, highlight(number).colorAt(0), "'$number' should be colored as number")
        }
    }

    @Test
    fun `bigint literals are colored as number`() = runTest {
        for (number in listOf("42n", "0xFFn")) {
            assertEquals(testColors.number, highlight(number).colorAt(0), "'$number' should be colored as number")
        }
    }
}
