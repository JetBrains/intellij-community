// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jewel.bridge.code.highlighting

import androidx.compose.ui.graphics.Color
import com.intellij.testFramework.LightPlatform4TestCase
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Test

internal class IntelliJCodeHighlighterTest : LightPlatform4TestCase() {
    private lateinit var highlighter: IntelliJCodeHighlighter

    override fun setUp() {
        super.setUp()
        highlighter = IntelliJCodeHighlighter(project, emptyFlow())
    }

    @Test
    fun `should highlight html code using native highlighter`() = runTest {
        val code = "<p>Hello, Text!</p>"
        val languageName = "HTML"

        val annotatedString = highlighter.highlight(code, languageName).first()

        assertTrue("HTML code should have been highlighted", annotatedString.spanStyles.isNotEmpty())
        assertFalse(
            "At least one token should have a color",
            annotatedString.spanStyles.all { it.item.color == Color.Unspecified },
        )
    }

    // We need the TextMate plugin installed for this test to pass
    @Test
    fun `should highlight unsupported language using TextMate fallback`() = runTest {
        val code =
            """
            def main():
                println("Hi, this is some neat Python code!")
            """
                .trimIndent()
        val langName = "py" // Assuming Python plugin isn't installed, so it can fall back to TextMate

        val annotatedString = highlighter.highlight(code, langName).first()

        assertTrue("Python code should be highlighted by TextMate", annotatedString.spanStyles.size > 1)
    }

    @Test
    fun `should return plain text for unknown language`() = runTest {
        val code = "some random text"
        val langName = "not-a-real-language"

        val annotatedString = highlighter.highlight(code, langName).first()

        assertTrue("Unknown language should not be highlighted", annotatedString.spanStyles.isEmpty())
    }

    @Test
    fun `should return plain text for blank language name`() = runTest {
        val code = "public class Test {}"

        val annotatedString = highlighter.highlight(code, language = "").first()

        assertEquals(code, annotatedString.text)
        assertTrue(annotatedString.spanStyles.isEmpty())
    }
}
