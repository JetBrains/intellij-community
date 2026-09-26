// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jewel.intui.code.highlighting.languages

import androidx.compose.ui.text.font.FontStyle
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

internal class JSONGrammarTest {
    private val highlighter = SimpleCodeHighlighter(testColors)

    private suspend fun highlight(code: String, language: String = "json") =
        highlighter.highlight(code, language).first()

    @Test
    fun `name and all aliases are recognized`() = runTest {
        val identifiers =
            listOf(
                "json",
                "4dform",
                "4dproject",
                "avsc",
                "bowerrc",
                "cssmap",
                "geojson",
                "gltf",
                "har",
                "ice",
                "ipynb",
                "jscsrc",
                "jslintrc",
                "jsmap",
                "json.example",
                "json-tmlanguage",
                "jsonl",
                "jsonld",
                "mcmeta",
                "sarif",
                "slnlaunch",
                "tact",
                "tfstate",
                "tfstate.backup",
                "topojson",
                "tsmap",
                "vuerc",
                "webapp",
                "webmanifest",
                "yy",
                "yyp",
            )
        for (identifier in identifiers) {
            assertTrue(
                highlight("""{"a": 1}""", identifier).spanStyles.isNotEmpty(),
                "Alias '$identifier' not recognized",
            )
        }
    }

    @Test
    fun `language tags are matched case-insensitively`() = runTest {
        for (tag in listOf("JSON", "Json", "GeoJSON")) {
            assertTrue(highlight("""{"a": 1}""", tag).spanStyles.isNotEmpty(), "Tag '$tag' not recognized")
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
    fun `doc-style block comment is colored as comment`() = runTest {
        assertEquals(testColors.comment, highlight("/** doc */").colorAt(0))
    }

    @Test
    fun `comments are italic`() = runTest {
        assertEquals(FontStyle.Italic, highlight("// comment").spansAt(0).first().fontStyle)
    }

    @Test
    fun `constant inside comment is not colored as constant`() = runTest {
        val result = highlight("// true")
        assertEquals(testColors.comment, result.colorAt(3))
        assertNotEquals(testColors.constant, result.colorAt(3))
    }

    @Test
    fun `object key is colored as property key`() = runTest {
        assertEquals(testColors.propertyKey, highlight("""{"a": 1}""").colorAt(1))
    }

    @Test
    fun `string value is colored as string`() = runTest {
        assertEquals(testColors.string, highlight("""{"a": "b"}""").colorAt(6))
    }

    @Test
    fun `key and value with identical text are colored differently`() = runTest {
        val result = highlight("""{"a": "a"}""")
        assertEquals(testColors.propertyKey, result.colorAt(1), "Key should be a property key")
        assertEquals(testColors.string, result.colorAt(6), "Value should be a string")
    }

    @Test
    fun `colon is not part of the key span`() = runTest {
        // The key rule's lookahead is zero-width, so the match ends at the closing quote (index 3)
        assertNull(highlight("""{"a": 1}""").colorAt(4))
    }

    @Test
    fun `whitespace between key and colon is allowed`() = runTest {
        assertEquals(testColors.propertyKey, highlight("""{"a" : 1}""").colorAt(1))
    }

    @Test
    fun `key that looks like a language constant is still a key`() = runTest {
        val result = highlight("""{"true": 1}""")
        assertEquals(testColors.propertyKey, result.colorAt(2))
        assertNotEquals(testColors.constant, result.colorAt(2))
    }

    @Test
    fun `escaped quote does not end the key early`() = runTest {
        // Key is `a\":b`, so the quote at index 4 must not terminate it
        val result = highlight("""{"a\":b": 1}""")
        assertEquals(testColors.propertyKey, result.colorAt(6))
        assertEquals(testColors.number, result.colorAt(10))
    }

    @Test
    fun `string in an array is not treated as a key`() = runTest {
        assertEquals(testColors.string, highlight("""["a", "b"]""").colorAt(1))
    }

    @Test
    fun `language constants are colored as constant`() = runTest {
        for (constant in listOf("true", "false", "null")) {
            assertEquals(
                testColors.constant,
                highlight(constant).colorAt(0),
                "'$constant' should be colored as constant",
            )
        }
    }

    @Test
    fun `constant inside string is not colored as constant`() = runTest {
        val result = highlight("""{"a": "true"}""")
        assertEquals(testColors.string, result.colorAt(7))
        assertNotEquals(testColors.constant, result.colorAt(7))
    }

    @Test
    fun `numbers are colored as number`() = runTest {
        for (number in listOf("0", "42", "-1", "3.14", "1e10", "-1.5e+10")) {
            assertEquals(testColors.number, highlight(number).colorAt(0), "'$number' should be colored as number")
        }
    }

    @Test
    fun `numbers in arrays are colored as number`() = runTest {
        // Numbers are not gated on a preceding colon
        val result = highlight("[1, 2, 3]")
        assertEquals(testColors.number, result.colorAt(1))
        assertEquals(testColors.number, result.colorAt(4))
    }
}
