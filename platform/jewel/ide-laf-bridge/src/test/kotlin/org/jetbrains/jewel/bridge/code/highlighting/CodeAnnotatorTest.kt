// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jewel.bridge.code.highlighting

import androidx.compose.ui.text.font.FontWeight
import com.intellij.lang.Language
import com.intellij.lexer.Lexer
import com.intellij.openapi.editor.colors.EditorColorsScheme
import com.intellij.openapi.editor.colors.TextAttributesKey
import com.intellij.openapi.editor.markup.EffectType
import com.intellij.openapi.editor.markup.TextAttributes
import com.intellij.openapi.fileTypes.SyntaxHighlighter
import com.intellij.psi.tree.IElementType
import io.mockk.every
import io.mockk.justRun
import io.mockk.mockk
import java.awt.Color
import java.awt.Font
import junit.framework.TestCase.assertEquals
import junit.framework.TestCase.assertTrue
import org.jetbrains.annotations.NonNls
import org.jetbrains.jewel.bridge.toAwtColorOrNull
import org.junit.Assert.assertNotNull
import org.junit.Test

internal class CodeAnnotatorTest {
    private val codeAnnotator = CodeAnnotator()
    private val lexer = mockk<Lexer>()
    private val highlighter = mockk<SyntaxHighlighter> { every { highlightingLexer } returns lexer }
    private val colorScheme = mockk<EditorColorsScheme>()

    @Test
    fun `returns empty annotated string for empty code`() {
        val code = ""
        justRun { lexer.start(any()) }
        every { lexer.tokenType } returns null // Lexer immediately returns no tokens

        val annotatedString = codeAnnotator.annotate(code, highlighter, colorScheme)

        assertEquals("", annotatedString.text)
        assertTrue(annotatedString.spanStyles.isEmpty())
    }

    @Test
    fun `returns plain text when no highlighting rules match`() {
        val code = "Hello reviewer!"

        prepareLexer(
            listOf(
                TestTokenTypes.IDENTIFIER to "Hello",
                TestTokenTypes.WHITESPACE to " ",
                TestTokenTypes.IDENTIFIER to "reviewer!",
            )
        )

        every { highlighter.getTokenHighlights(any()) } returns emptyArray()

        val annotatedString = codeAnnotator.annotate(code, highlighter, colorScheme)

        assertEquals(code, annotatedString.text)
        assertTrue(annotatedString.spanStyles.isEmpty())
    }

    @Test
    fun `applies correct bold style for keyword that should be highlighted`() {
        val code = "fun main"

        prepareLexer(
            listOf(
                TestTokenTypes.KEYWORD to "fun",
                TestTokenTypes.WHITESPACE to " ",
                TestTokenTypes.IDENTIFIER to "main",
            )
        )

        val keywordAttrsKey = TextAttributesKey.createTextAttributesKey("TEST_KEYWORD")
        every { highlighter.getTokenHighlights(any()) } returns emptyArray()
        every { highlighter.getTokenHighlights(TestTokenTypes.KEYWORD) } returns arrayOf(keywordAttrsKey)

        val keywordAttributes = TextAttributes(Color.BLUE, null, null, EffectType.BOXED, Font.BOLD)
        every { colorScheme.getAttributes(keywordAttrsKey) } returns keywordAttributes

        val annotatedString = codeAnnotator.annotate(code, highlighter, colorScheme)

        assertEquals(code, annotatedString.text)

        val keywordStyle = annotatedString.spanStyles.find { it.start == 0 && it.end == 3 }
        assertNotNull("A style should be applied to the 'fun' keyword", keywordStyle)

        assertEquals(FontWeight.Bold, keywordStyle?.item?.fontWeight)
        assertEquals(Color.BLUE, keywordStyle?.item?.color?.toAwtColorOrNull())
    }

    private fun prepareLexer(tokenSequence: List<Pair<IElementType, String>>) {
        var currentIndex = -1

        every { lexer.tokenType } answers { tokenSequence.getOrNull(currentIndex)?.first }
        every { lexer.tokenText } answers { tokenSequence.getOrNull(currentIndex)?.second.orEmpty() }
        every { lexer.start(any()) } answers { currentIndex = 0 }
        every { lexer.advance() } answers { currentIndex++ }
    }
}

private class TestTokenType(@NonNls debugName: String) : IElementType(debugName, Language.ANY)

private object TestTokenTypes {
    val KEYWORD = TestTokenType("KEYWORD")
    val IDENTIFIER = TestTokenType("IDENTIFIER")
    val WHITESPACE = TestTokenType("WHITESPACE")
    val COMMENT = TestTokenType("COMMENT")
}
