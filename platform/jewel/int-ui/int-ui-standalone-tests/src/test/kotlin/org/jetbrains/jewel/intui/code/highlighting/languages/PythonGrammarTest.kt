// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jewel.intui.code.highlighting.languages

import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.jetbrains.jewel.intui.code.highlighting.colorAt
import org.jetbrains.jewel.intui.code.highlighting.testColors
import org.jetbrains.jewel.intui.standalone.code.highlighting.SimpleCodeHighlighter
import org.junit.jupiter.api.Test

internal class PythonGrammarTest {
    private val highlighter = SimpleCodeHighlighter(testColors)

    private suspend fun highlight(code: String, language: String = "python") =
        highlighter.highlight(code, language).first()

    @Test
    fun `name and all aliases are recognized`() = runTest {
        val identifiers =
            listOf("python", "py", "py3", "python3", "rpy", "pyw", "cpy", "gyp", "gypi", "pyi", "ipy", "pyt")
        for (identifier in identifiers) {
            assertTrue(highlight("x = 1", identifier).spanStyles.isNotEmpty(), "Alias '$identifier' not recognized")
        }
    }

    @Test
    fun `comment is colored as comment`() = runTest {
        assertEquals(testColors.comment, highlight("# a comment").colorAt(0))
    }

    @Test
    fun `keyword inside comment is not colored as keyword`() = runTest {
        val result = highlight("# import os")
        assertEquals(testColors.comment, result.colorAt(2))
        assertNotEquals(testColors.keyword, result.colorAt(2))
    }

    @Test
    fun `single-quoted string is colored as string`() = runTest {
        assertEquals(testColors.string, highlight("'hello'").colorAt(0))
    }

    @Test
    fun `triple-quoted string is colored as string`() = runTest {
        assertEquals(testColors.string, highlight("\"\"\"docstring\"\"\"").colorAt(0))
    }

    @Test
    fun `prefixed strings are colored as string`() = runTest {
        for (literal in listOf("f\"hi\"", "r'hi'", "b\"hi\"", "rb\"hi\"")) {
            assertEquals(testColors.string, highlight(literal).colorAt(0), "'$literal' should be colored as string")
        }
    }

    @Test
    fun `keyword inside string is not colored as keyword`() = runTest {
        val result = highlight("\"import os\"")
        assertEquals(testColors.string, result.colorAt(1))
        assertNotEquals(testColors.keyword, result.colorAt(1))
    }

    @Test
    fun `def declaration colors keyword and name separately`() = runTest {
        val result = highlight("def my_func():")
        assertEquals(testColors.keyword, result.colorAt(0)) // "def"
        assertEquals(testColors.functionCall, result.colorAt(4)) // "my_func"
    }

    @Test
    fun `class declaration colors keyword and name separately`() = runTest {
        val result = highlight("class Foo:")
        assertEquals(testColors.keyword, result.colorAt(0)) // "class"
        assertEquals(testColors.type, result.colorAt(6)) // "Foo"
    }

    @Test
    fun `decorator is colored as function call`() = runTest {
        assertEquals(testColors.functionCall, highlight("@property").colorAt(0))
        assertEquals(testColors.functionCall, highlight("@app.route").colorAt(0))
    }

    @Test
    fun `keywords are colored as keyword`() = runTest {
        val keywords = listOf("def", "class", "lambda", "return", "import", "if", "elif", "else", "for", "while")
        for (keyword in keywords) {
            assertEquals(testColors.keyword, highlight(keyword).colorAt(0), "'$keyword' should be colored as keyword")
        }
    }

    @Test
    fun `word operators are colored as operator`() = runTest {
        // #operator scopes these as keyword.operator.logical.python, which is not the keyword key
        for (word in listOf("in", "is", "not", "and", "or")) {
            assertEquals(testColors.operator, highlight(word).colorAt(0), "'$word' should be an operator")
        }
    }

    @Test
    fun `language constants are colored as constant`() = runTest {
        for (constant in listOf("True", "False", "None", "NotImplemented", "Ellipsis")) {
            assertEquals(
                testColors.constant,
                highlight(constant).colorAt(0),
                "'$constant' should be colored as constant",
            )
        }
    }

    @Test
    fun `builtin types are colored as builtin`() = runTest {
        // support.type.python maps to IntelliJ's predefined-symbol key
        for (type in listOf("int", "str", "float", "bool", "dict", "list", "set", "tuple")) {
            assertEquals(testColors.builtin, highlight(type).colorAt(0), "'$type' should be a builtin")
        }
    }

    @Test
    fun `builtin functions are colored as builtin`() = runTest {
        for (builtin in listOf("print", "len", "range", "self", "cls", "isinstance")) {
            assertEquals(testColors.builtin, highlight(builtin).colorAt(0), "'$builtin' should be colored as builtin")
        }
    }

    @Test
    fun `call site is colored as function call`() = runTest {
        assertEquals(testColors.functionCall, highlight("my_func(1)").colorAt(0))
    }

    @Test
    fun `builtins keep their color at call sites`() = runTest {
        // The builtin rule is listed before functionCall, so `print(` stays a builtin
        assertEquals(testColors.builtin, highlight("print(1)").colorAt(0))
    }

    @Test
    fun `numbers are colored as number`() = runTest {
        val numbers =
            listOf("42", "0", "0x1F", "0xdead_beef", "0b1010", "0o755", "3.14", "1e10", "1E-5", "2j", "3.5j", "1_000")
        for (number in numbers) {
            assertEquals(testColors.number, highlight(number).colorAt(0), "'$number' should be colored as number")
        }
    }

    @Test
    fun `leading-dot floats are colored from the dot`() = runTest {
        // #number-float's first branch. A rule anchored to a leading digit leaves the dot plain.
        assertEquals(testColors.number, highlight("x = .5").colorAt(4), "the dot")
        assertEquals(testColors.number, highlight("x = .5").colorAt(5), "the digit")
        assertEquals(testColors.number, highlight("x = .5e2").colorAt(4), "with an exponent")
        assertEquals(testColors.number, highlight("x = .5e2").colorAt(7), "through the exponent")
    }

    @Test
    fun `underscores are allowed in the exponent`() = runTest {
        // `1e1_0` is valid Python and matched nothing before the exponent allowed separators
        assertEquals(testColors.number, highlight("x = 1e1_0").colorAt(4))
        assertEquals(testColors.number, highlight("x = 1e1_0").colorAt(8))
    }

    @Test
    fun `a digit inside an identifier is not a number`() = runTest {
        assertNull(highlight("x = a1").colorAt(5))
        assertNull(highlight("x = _1").colorAt(5))
    }
}
