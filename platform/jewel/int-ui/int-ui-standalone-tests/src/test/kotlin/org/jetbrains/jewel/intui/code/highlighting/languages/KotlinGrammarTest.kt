// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jewel.intui.code.highlighting.languages

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.jetbrains.jewel.intui.code.highlighting.colorAt
import org.jetbrains.jewel.intui.code.highlighting.spansAt
import org.jetbrains.jewel.intui.code.highlighting.testColors
import org.jetbrains.jewel.intui.standalone.code.highlighting.SimpleCodeHighlighter
import org.junit.jupiter.api.Test

internal class KotlinGrammarTest {
    private val highlighter = SimpleCodeHighlighter(testColors)

    private suspend fun highlight(code: String, language: String = "kotlin") =
        highlighter.highlight(code, language).first()

    @Test
    fun `all kotlin aliases are recognized`() = runTest {
        for (alias in listOf("kotlin", "kt", "kts")) {
            assertTrue(highlight("val x = 1", alias).spanStyles.isNotEmpty(), "Alias '$alias' not recognized")
        }
    }

    @Test
    fun `line comment is colored as comment`() = runTest {
        val result = highlight("// this is a comment")
        assertEquals(Color.Gray, result.colorAt(0))
    }

    @Test
    fun `line comment is italic`() = runTest {
        val result = highlight("// comment")
        assertEquals(FontStyle.Italic, result.spansAt(0).first().fontStyle)
    }

    @Test
    fun `block comment is colored as comment`() = runTest {
        assertEquals(Color.Gray, highlight("/* block */").colorAt(0))
    }

    @Test
    fun `keyword inside line comment is not colored as keyword`() = runTest {
        val result = highlight("// val fun class")
        assertNotEquals(Color.Red, result.colorAt(3)) // 'v' of val
        assertEquals(Color.Gray, result.colorAt(3))
    }

    @Test
    fun `keyword inside block comment is not colored as keyword`() = runTest {
        val result = highlight("/* val fun */")
        assertNotEquals(Color.Red, result.colorAt(3))
        assertEquals(Color.Gray, result.colorAt(3))
    }

    @Test
    fun `double-quoted string is colored as string`() = runTest {
        assertEquals(Color.Cyan, highlight("\"hello world\"").colorAt(0))
    }

    @Test
    fun `triple-quoted string is colored as string`() = runTest {
        assertEquals(Color.Cyan, highlight("\"\"\"multi\nline\"\"\"").colorAt(0))
    }

    @Test
    fun `keyword inside string is not colored as keyword`() = runTest {
        val result = highlight("\"val fun class\"")
        assertEquals(Color.Cyan, result.colorAt(1)) // 'v' of val inside string
        assertNotEquals(Color.Red, result.colorAt(1))
    }

    @Test
    fun `fun declaration colors keyword and function name separately`() = runTest {
        val result = highlight("fun myFunc() {}")
        assertEquals(Color.Red, result.colorAt(0)) // "fun" → keyword
        assertEquals(Color.Yellow, result.colorAt(4)) // "myFunc" → function call
    }

    @Test
    fun `fun keyword in declaration is bold`() = runTest {
        val result = highlight("fun myFunc() {}")
        assertEquals(FontWeight.Bold, result.spansAt(0).first().fontWeight)
    }

    @Test
    fun `class declaration colors keyword and class name separately`() = runTest {
        val result = highlight("class MyClass")
        assertEquals(Color.Red, result.colorAt(0)) // "class" → keyword
        assertEquals(Color.White, result.colorAt(6)) // "MyClass" → builtin
    }

    @Test
    fun `storage keywords are colored as keyword`() = runTest {
        for (keyword in listOf("val", "var", "fun", "class", "suspend", "override", "data", "sealed", "inline")) {
            val result = highlight(keyword)
            assertEquals(Color.Red, result.colorAt(0), "'$keyword' should be colored as keyword")
        }
    }

    @Test
    fun `keywords are bold`() = runTest {
        for (keyword in listOf("val", "var", "fun")) {
            val result = highlight(keyword)
            assertEquals(FontWeight.Bold, result.spansAt(0).first().fontWeight, "'$keyword' should be bold")
        }
    }

    @Test
    fun `control keywords are colored as keyword`() = runTest {
        for (keyword in listOf("if", "else", "when", "for", "while", "return", "throw", "try", "catch")) {
            val result = highlight(keyword)
            assertEquals(Color.Red, result.colorAt(0), "'$keyword' should be colored as keyword")
        }
    }

    @Test
    fun `control keywords before parenthesis are not colored as function calls`() = runTest {
        // functionCall rule comes after keyword rules — keyword wins on ties
        for (keyword in listOf("if", "while", "for", "when", "catch")) {
            val result = highlight("$keyword (")
            assertEquals(Color.Red, result.colorAt(0), "'$keyword' should stay keyword, not become function call")
            assertNotEquals(Color.Yellow, result.colorAt(0))
        }
    }

    @Test
    fun `language constants are colored as constant`() = runTest {
        for (constant in listOf("true", "false", "null")) {
            assertEquals(Color.Green, highlight(constant).colorAt(0), "'$constant' should be colored as constant")
        }
    }

    @Test
    fun `built-in types are colored as type`() = runTest {
        for (type in listOf("String", "Int", "Long", "Boolean", "List", "Map", "Unit", "Any")) {
            assertEquals(Color.Blue, highlight(type).colorAt(0), "'$type' should be colored as type")
        }
    }

    @Test
    fun `decimal integers are colored as number`() = runTest {
        for (number in listOf("42", "1_000_000", "42L")) {
            assertEquals(Color.Magenta, highlight(number).colorAt(0), "'$number' should be colored as number")
        }
    }

    @Test
    fun `decimal floats and scientific notation are colored as number`() = runTest {
        for (number in listOf("3.14", "2.5f", "1e10", "6.02e23")) {
            assertEquals(Color.Magenta, highlight(number).colorAt(0), "'$number' should be colored as number")
        }
    }

    @Test
    fun `hex numbers are colored as number`() = runTest {
        assertEquals(Color.Magenta, highlight("0xDEADBEEF").colorAt(0))
    }

    @Test
    fun `binary numbers are colored as number`() = runTest {
        assertEquals(Color.Magenta, highlight("0b1010_1010").colorAt(0))
    }

    @Test
    fun `unsigned integer literals are colored as number`() = runTest {
        for (number in listOf("42u", "42U", "42uL", "42UL")) {
            assertEquals(Color.Magenta, highlight(number).colorAt(0), "'$number' should be colored as number")
        }
    }

    @Test
    fun `function call site is colored as function call`() = runTest {
        assertEquals(Color.Yellow, highlight("tomato()").colorAt(0))
    }

    @Test
    fun `stdlib call site is colored as function call`() = runTest {
        assertEquals(Color.Yellow, highlight("println(\"hi\")").colorAt(0))
    }
}
