// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jewel.intui.code.highlighting.languages

import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.jetbrains.jewel.intui.code.highlighting.colorAt
import org.jetbrains.jewel.intui.code.highlighting.testColors
import org.jetbrains.jewel.intui.standalone.code.highlighting.SimpleCodeHighlighter
import org.junit.jupiter.api.Test

internal class CGrammarTest {
    private val highlighter = SimpleCodeHighlighter(testColors)

    private suspend fun highlight(code: String, language: String = "c") = highlighter.highlight(code, language).first()

    @Test
    fun `name and all aliases are recognized`() = runTest {
        for (identifier in listOf("c", "h", "i", "cats", "idc")) {
            assertTrue(highlight("int x;", identifier).spanStyles.isNotEmpty(), "Alias '$identifier' not recognized")
        }
    }

    @Test
    fun `line and block comments are colored as comment`() = runTest {
        assertEquals(testColors.comment, highlight("// a comment").colorAt(0))
        assertEquals(testColors.comment, highlight("/* a\nb */").colorAt(0))
        assertEquals(testColors.comment, highlight("int x; // trailing").colorAt(7))
    }

    @Test
    fun `keywords and strings inside a comment are not colored separately`() = runTest {
        assertEquals(testColors.comment, highlight("// int x").colorAt(3))
        assertEquals(testColors.comment, highlight("/* \"str\" */").colorAt(4))
    }

    @Test
    fun `strings and character literals are colored as string`() = runTest {
        assertEquals(testColors.string, highlight("puts(\"hi\");").colorAt(5))
        assertEquals(testColors.string, highlight("char c = 'a';").colorAt(9))
        assertEquals(testColors.string, highlight("puts(\"a\\\"b\");").colorAt(5))
    }

    @Test
    fun `an escape inside a string stays string-colored`() = runTest {
        // The bundle scopes \n and %d separately, but only reaches those rules inside the string
        assertEquals(testColors.string, highlight("puts(\"%d\\n\");").colorAt(6))
    }

    @Test
    fun `control flow keywords are colored as keyword`() = runTest {
        for (keyword in listOf("break", "continue", "do", "else", "for", "goto", "if", "return", "while")) {
            assertEquals(testColors.keyword, highlight("$keyword ").colorAt(0), "'$keyword' should be a keyword")
        }
    }

    @Test
    fun `switch case and default are colored as keyword`() = runTest {
        for (keyword in listOf("switch", "case", "default")) {
            assertEquals(testColors.keyword, highlight("$keyword ").colorAt(0), "'$keyword' should be a keyword")
        }
    }

    @Test
    fun `aggregate keywords are colored as keyword`() = runTest {
        // The bundle files enum, struct and union under storage.type; we read them as declaration keywords
        for (keyword in listOf("enum", "struct", "union")) {
            assertEquals(testColors.keyword, highlight("$keyword S").colorAt(0), "'$keyword' should be a keyword")
        }
    }

    @Test
    fun `storage modifiers and typedef are colored as keyword`() = runTest {
        for (keyword in listOf("const", "extern", "register", "restrict", "static", "volatile", "inline")) {
            assertEquals(testColors.keyword, highlight("$keyword int x").colorAt(0), "'$keyword' should be a keyword")
        }
        assertEquals(testColors.keyword, highlight("typedef int myint;").colorAt(0))
    }

    @Test
    fun `a keyword appearing inside an identifier is not colored`() = runTest {
        // The bundle's typedef rule is the bare word, so without the \b pair we add, the substring matches
        assertNull(highlight("int mytypedefName;").colorAt(6))
        assertNull(highlight("int my_size_typedef;").colorAt(12))
        // Same check for the neighbours that already carried boundaries
        assertNull(highlight("int constant;").colorAt(4))
        assertNull(highlight("int switcher;").colorAt(4))
        assertNull(highlight("int defaults;").colorAt(4))
    }

    @Test
    fun `storage type names are colored as keyword`() = runTest {
        // IntelliJ maps the whole storage.type family to its keyword key, so `int` is not a TYPE here
        for (type in listOf("int", "char", "void", "float", "double", "unsigned", "short", "long", "_Bool")) {
            assertEquals(testColors.keyword, highlight("$type x").colorAt(0), "'$type' should be a keyword")
        }
        // These are in storage.type.built-in.c as well as in a support.type table, and the bundle lists
        // #storage_types first
        for (type in listOf("size_t", "uint32_t", "pthread_t", "time_t", "ssize_t")) {
            assertEquals(testColors.keyword, highlight("$type x").colorAt(0), "'$type' should be a keyword")
        }
    }

    @Test
    fun `support type names are colored as builtin`() = runTest {
        // support.type maps to IntelliJ's predefined-symbol key, which is our BUILTIN
        assertEquals(testColors.builtin, highlight("UInt32 x").colorAt(0))
        assertEquals(testColors.builtin, highlight("my_custom_t x").colorAt(0), "support.type.posix-reserved")
    }

    @Test
    fun `language constants are colored as constant`() = runTest {
        for (constant in listOf("NULL", "true", "false", "TRUE", "FALSE")) {
            assertEquals(testColors.constant, highlight("x = $constant;").colorAt(4), "'$constant' is a constant")
        }
    }

    @Test
    fun `predefined macros are colored as constant`() = runTest {
        assertEquals(testColors.constant, highlight("__FILE__").colorAt(0))
    }

    @Test
    fun `mac classic prefixes are honored`() = runTest {
        assertEquals(testColors.constant, highlight("kMaxSize").colorAt(0))
        assertEquals(testColors.builtin, highlight("gState").colorAt(0))
        assertEquals(testColors.builtin, highlight("sBuffer").colorAt(0))
    }

    @Test
    fun `numbers are colored as number`() = runTest {
        for (number in listOf("42", "0xFF", "0b1010", "0755", "3.14", "1e-10", "3.14f", "10UL")) {
            assertEquals(testColors.number, highlight("x = $number;").colorAt(4), "'$number' should be a number")
        }
    }

    @Test
    fun `function calls and definitions color the name`() = runTest {
        assertEquals(testColors.functionCall, highlight("puts(\"hi\");").colorAt(0))
        assertEquals(testColors.functionCall, highlight("int main(void) { return 0; }").colorAt(4))
    }

    @Test
    fun `a keyword followed by a paren is not a function call`() = runTest {
        for (keyword in listOf("if", "while", "return", "for", "switch")) {
            assertEquals(testColors.keyword, highlight("$keyword (x)").colorAt(0), "'$keyword (' should stay a keyword")
        }
        // sizeof is keyword.operator.sizeof.c in the bundle, not a control keyword
        assertEquals(testColors.operator, highlight("sizeof (x)").colorAt(0))
    }

    @Test
    fun `the call rule does not slide past a word it excludes`() = runTest {
        // The bundle's exclusion list is an unanchored lookahead. `catch` is on it but is not a C keyword,
        // so without the leading boundary we added the rule would fail at `c` and match `atch (` instead.
        val result = highlight("catch (x)")
        assertNull(result.colorAt(0))
        assertNull(result.colorAt(1))
    }

    @Test
    fun `member access colors the member but not the object`() = runTest {
        assertEquals(testColors.builtin, highlight("p->count").colorAt(3))
        assertNull(highlight("p->count").colorAt(0))
        assertEquals(testColors.builtin, highlight("s.len").colorAt(2))
    }

    @Test
    fun `member access wins over the operator rules`() = runTest {
        // The match starts at the `-`, where the minus operator ties with it
        assertEquals(testColors.builtin, highlight("argv[i]->len").colorAt(9))
        assertEquals(testColors.operator, highlight("a - b").colorAt(2), "a bare minus is still an operator")
    }

    @Test
    fun `preprocessor directives are colored as keyword`() = runTest {
        for (directive in listOf("if", "ifdef", "ifndef", "elif", "else", "endif")) {
            assertEquals(testColors.keyword, highlight("#$directive X\n").colorAt(0), "'#$directive'")
        }
        for (directive in listOf("pragma once", "error nope", "undef X", "line 5")) {
            assertEquals(testColors.keyword, highlight("#$directive").colorAt(0), "'#$directive'")
        }
    }

    @Test
    fun `include colors the directive and the path`() = runTest {
        assertEquals(testColors.keyword, highlight("#include <stdio.h>").colorAt(0))
        assertEquals(testColors.string, highlight("#include <stdio.h>").colorAt(10))
        assertEquals(testColors.string, highlight("#include \"local.h\"").colorAt(10))
    }

    @Test
    fun `define colors the directive and the macro name`() = runTest {
        assertEquals(testColors.keyword, highlight("#define MAX 10").colorAt(0))
        assertEquals(testColors.functionCall, highlight("#define MAX 10").colorAt(8))
        assertEquals(testColors.functionCall, highlight("#define SQ(x) ((x)*(x))").colorAt(8))
    }

    @Test
    fun `an if 0 block is greyed out`() = runTest {
        val result = highlight("#if 0\ndead();\n#endif\n")
        assertEquals(testColors.comment, result.colorAt(0))
        assertEquals(testColors.comment, result.colorAt(6))
    }

    @Test
    fun `an if 1 block stays live`() = runTest {
        assertEquals(testColors.functionCall, highlight("#if 1\nlive();\n#endif\n").colorAt(6))
    }

    @Test
    fun `operators are colored as operator`() = runTest {
        // keyword.operator.* is DEFAULT_OPERATION_SIGN in IntelliJ, a different key from keywords
        for (expression in listOf("a + b", "a << b", "a == b", "a && b")) {
            assertEquals(testColors.operator, highlight(expression).colorAt(2), "'$expression'")
        }
        assertEquals(testColors.operator, highlight("i++").colorAt(1))
        assertEquals(testColors.operator, highlight("a ? b : c").colorAt(2))
        assertEquals(testColors.operator, highlight("sizeof(x)").colorAt(0))
    }
}
