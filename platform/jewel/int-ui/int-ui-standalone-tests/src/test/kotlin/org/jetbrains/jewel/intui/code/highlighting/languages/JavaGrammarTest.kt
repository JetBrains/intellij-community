// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jewel.intui.code.highlighting.languages

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.jetbrains.jewel.intui.code.highlighting.colorAt
import org.jetbrains.jewel.intui.code.highlighting.spansAt
import org.jetbrains.jewel.intui.code.highlighting.testColors
import org.jetbrains.jewel.intui.standalone.code.highlighting.SimpleCodeHighlighter
import org.junit.jupiter.api.Test

internal class JavaGrammarTest {
    private val highlighter = SimpleCodeHighlighter(testColors)

    private suspend fun highlight(code: String) = highlighter.highlight(code, "java").first()

    @Test
    fun `line comment is colored as comment`() = runTest {
        assertEquals(Color.Gray, highlight("// a comment").colorAt(0))
    }

    @Test
    fun `line comment is italic`() = runTest {
        assertEquals(FontStyle.Italic, highlight("// comment").spansAt(0).first().fontStyle)
    }

    @Test
    fun `block comment is colored as comment`() = runTest {
        assertEquals(Color.Gray, highlight("/* block */").colorAt(0))
    }

    @Test
    fun `keyword inside line comment is not colored as keyword`() = runTest {
        val result = highlight("// public static void")
        assertNotEquals(Color.Red, result.colorAt(3))
        assertEquals(Color.Gray, result.colorAt(3))
    }

    @Test
    fun `double-quoted string is colored as string`() = runTest {
        assertEquals(Color.Cyan, highlight("\"hello\"").colorAt(0))
    }

    @Test
    fun `text block is colored as string`() = runTest {
        assertEquals(Color.Cyan, highlight("\"\"\"hello\"\"\"").colorAt(0))
    }

    @Test
    fun `keyword inside string is not colored as keyword`() = runTest {
        val result = highlight("\"public static\"")
        assertEquals(Color.Cyan, result.colorAt(1))
        assertNotEquals(Color.Red, result.colorAt(1))
    }

    @Test
    fun `method name before parenthesis is colored as function call`() = runTest {
        // "main" starts at position 12 in "public void main()"
        assertEquals(Color.Yellow, highlight("public void main()").colorAt(12))
    }

    @Test
    fun `method call site is colored as function call`() = runTest {
        assertEquals(Color.Yellow, highlight("System.out.println()").colorAt(11)) // "println" at 11
    }

    @Test
    fun `modifiers and declaration keywords are colored as keyword`() = runTest {
        for (keyword in listOf("public", "private", "static", "final", "class", "interface", "return", "new")) {
            val result = highlight(keyword)
            assertEquals(Color.Red, result.colorAt(0), "'$keyword' should be colored as keyword")
        }
    }

    @Test
    fun `keywords are bold`() = runTest {
        for (keyword in listOf("public", "static", "final")) {
            assertEquals(FontWeight.Bold, highlight(keyword).spansAt(0).first().fontWeight, "'$keyword' should be bold")
        }
    }

    @Test
    fun `language constants are colored as constant`() = runTest {
        for (constant in listOf("true", "false", "null")) {
            assertEquals(Color.Green, highlight(constant).colorAt(0), "'$constant' should be colored as constant")
        }
    }

    @Test
    fun `primitive types are colored as type`() = runTest {
        for (type in listOf("boolean", "int", "long", "double", "float", "void", "char", "byte")) {
            assertEquals(Color.Blue, highlight(type).colorAt(0), "'$type' should be colored as type")
        }
    }

    @Test
    fun `common stdlib roots are colored as builtin`() = runTest {
        for (builtin in listOf("String", "Object", "System", "Math")) {
            assertEquals(Color.White, highlight(builtin).colorAt(0), "'$builtin' should be colored as builtin")
        }
    }

    @Test
    fun `decimal integers are colored as number`() = runTest {
        for (number in listOf("42", "42L", "1_000")) {
            assertEquals(Color.Magenta, highlight(number).colorAt(0), "'$number' should be colored as number")
        }
    }

    @Test
    fun `hex numbers are colored as number`() = runTest {
        assertEquals(Color.Magenta, highlight("0xDEADBEEF").colorAt(0))
    }

    @Test
    fun `binary numbers are colored as number`() = runTest {
        assertEquals(Color.Magenta, highlight("0b1010").colorAt(0))
    }

    @Test
    fun `floating point numbers are colored as number`() = runTest {
        for (number in listOf("3.14", "2.5f", "1.0d", "1e10")) {
            assertEquals(Color.Magenta, highlight(number).colorAt(0), "'$number' should be colored as number")
        }
    }
}
