// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jewel.intui.code.highlighting

import androidx.compose.ui.graphics.Color
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.jetbrains.jewel.intui.standalone.code.highlighting.LanguageGrammar
import org.jetbrains.jewel.intui.standalone.code.highlighting.SimpleCodeHighlighter
import org.jetbrains.jewel.intui.standalone.code.highlighting.TokenRule
import org.junit.jupiter.api.Test

internal class SimpleCodeHighlighterTest {
    private val highlighter = SimpleCodeHighlighter(testColors)

    private suspend fun highlight(code: String, language: String) = highlighter.highlight(code, language).first()

    @Test
    fun `blank language returns plain annotated string`() = runTest {
        assertTrue(highlight("val x = 1", "").spanStyles.isEmpty())
    }

    @Test
    fun `whitespace-only language returns plain annotated string`() = runTest {
        assertTrue(highlight("val x = 1", "   ").spanStyles.isEmpty())
    }

    @Test
    fun `unknown language returns plain annotated string`() = runTest {
        assertTrue(highlight("some code here", "brainfuck").spanStyles.isEmpty())
    }

    @Test
    fun `empty code returns empty annotated string`() = runTest {
        val result = highlight("", "kotlin")
        assertEquals("", result.text)
        assertTrue(result.spanStyles.isEmpty())
    }

    @Test
    fun `language lookup is case-insensitive`() = runTest {
        val lower = highlight("val x = 1", "kotlin")
        val upper = highlight("val x = 1", "KOTLIN")
        val mixed = highlight("val x = 1", "Kotlin")
        assertEquals(lower.spanStyles.size, upper.spanStyles.size)
        assertEquals(lower.spanStyles.size, mixed.spanStyles.size)
    }

    @Test
    fun `language tag with extra attributes resolves the base language`() = runTest {
        assertTrue(highlight("val x = 1", "kotlin ide runnable").spanStyles.isNotEmpty())
    }

    @Test
    fun `alias with extra attributes resolves the base language`() = runTest {
        assertTrue(highlight("val x = 1", "kts something").spanStyles.isNotEmpty())
    }

    @Test
    fun `partial language tag does not match`() = runTest {
        assertTrue(highlight("val x = 1", "kot").spanStyles.isEmpty())
    }

    @Test
    fun `grammar name containing a space matches info string with attributes`() = runTest {
        val custom = LanguageGrammar(name = "my lang", rules = listOf(TokenRule.keyword("\\bhello\\b")))
        val customHighlighter = SimpleCodeHighlighter(testColors, listOf(custom))
        assertTrue(customHighlighter.highlight("hello", "my lang").first().spanStyles.isNotEmpty())
        assertTrue(customHighlighter.highlight("hello", "my lang extra-attr").first().spanStyles.isNotEmpty())
        assertTrue(customHighlighter.highlight("hello", "my").first().spanStyles.isEmpty())
    }

    @Test
    fun `custom grammar alias is recognized`() = runTest {
        val custom =
            LanguageGrammar(name = "mylang", aliases = listOf("ml"), rules = listOf(TokenRule.keyword("\\bhello\\b")))
        val result = SimpleCodeHighlighter(testColors, listOf(custom)).highlight("hello", "ml").first()
        assertTrue(result.spanStyles.isNotEmpty())
    }

    @Test
    fun `additional grammars take precedence over built-in grammars`() = runTest {
        val override = LanguageGrammar(name = "kotlin", rules = listOf(TokenRule.comment(".*")))
        val result = SimpleCodeHighlighter(testColors, listOf(override)).highlight("val x = 1", "kotlin").first()
        assertEquals(Color.Gray, result.colorAt(0)) // Gray = comment in testColors
    }

    @Test
    fun `custom language grammar is recognized`() = runTest {
        val custom = LanguageGrammar(name = "mylang", rules = listOf(TokenRule.keyword("\\b(hello|world)\\b")))
        val result = SimpleCodeHighlighter(testColors, listOf(custom)).highlight("hello world", "mylang").first()
        assertEquals(Color.Red, result.colorAt(0)) // Red = keyword in testColors
        assertEquals(Color.Red, result.colorAt(6))
    }

    @Test
    fun `functionCall rule with no capturing group produces no spans`() = runTest {
        val rule = TokenRule.functionCall("\\b[A-Za-z_]\\w*\\b")
        val grammar = LanguageGrammar(name = "test", rules = listOf(rule))
        val result = SimpleCodeHighlighter(testColors, listOf(grammar)).highlight("myFunction()", "test").first()
        assertTrue(result.spanStyles.isEmpty(), "Expected no spans when capturing group is missing")
    }

    @Test
    fun `functionDeclaration rule with no capturing groups produces no spans`() = runTest {
        val rule = TokenRule.functionDeclaration("fun [A-Za-z_]\\w*")
        val grammar = LanguageGrammar(name = "test", rules = listOf(rule))
        val result = SimpleCodeHighlighter(testColors, listOf(grammar)).highlight("fun myFunc()", "test").first()
        assertTrue(result.spanStyles.isEmpty(), "Expected no spans when capturing groups are missing")
    }

    @Test
    fun `zero-length match pattern does not cause infinite loop`() = runTest {
        val rule = TokenRule.keyword("(?=\\w)") // lookahead matches at every word character position
        val grammar = LanguageGrammar(name = "test", rules = listOf(rule))
        val result = SimpleCodeHighlighter(testColors, listOf(grammar)).highlight("hello", "test").first()
        assertEquals("hello", result.text)
    }

    @Test
    fun `functionDeclaration rule with only one capturing group produces only the keyword span`() = runTest {
        val rule = TokenRule.functionDeclaration("\\b(fun)\\s+[A-Za-z_]\\w*")
        val grammar = LanguageGrammar(name = "test", rules = listOf(rule))
        val result = SimpleCodeHighlighter(testColors, listOf(grammar)).highlight("fun myFunc()", "test").first()
        assertEquals(1, result.spanStyles.size)
        assertEquals(Color.Red, result.colorAt(0)) // Red = keyword in testColors
        assertNull(result.colorAt(4)) // "myFunc" — no span for missing group 2
    }
}
