// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jewel.bridge.code.highlighting

import androidx.compose.ui.graphics.Color
import com.intellij.testFramework.LightPlatform4TestCase
import com.intellij.util.ui.UIUtil
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.last
import kotlinx.coroutines.runBlocking
import org.jetbrains.plugins.textmate.TextMateService
import org.jetbrains.plugins.textmate.TextMateServiceImpl
import org.junit.Test

@Suppress("RAW_RUN_BLOCKING")
internal class IntelliJCodeHighlighterTest : LightPlatform4TestCase() {
    private lateinit var highlighter: IntelliJCodeHighlighter

    override fun setUp() {
        super.setUp()

        val textMateService = TextMateService.getInstance() as TextMateServiceImpl

        // Calling this to ensure initialization happens
        textMateService.customHighlightingColors
        UIUtil.dispatchAllInvocationEvents()

        highlighter = IntelliJCodeHighlighter(project, flowOf(Unit))
    }

    @Test
    fun `should highlight html code using native highlighter`() {
        val code = "<p>Hello, Text!</p>"
        val languageName = "HTML"

        val annotatedString = runBlocking { highlighter.highlight(code, languageName).last() }

        assertTrue("HTML code should have been highlighted", annotatedString.spanStyles.isNotEmpty())
        assertFalse(
            "At least one token should have a color",
            annotatedString.spanStyles.all { it.item.color == Color.Unspecified },
        )
    }

    @Test
    fun `should highlight unsupported language using TextMate fallback`() {
        val code =
            $$"""
            |<?php
            |function greet() {
            |    $world = "Hello World";
            |    return $world;
            |}
            |
            |echo greet();
            |?>
            """
                .trimMargin()
        val languageName = "php" // Assuming the PHP plugin isn't installed, so it can fall back to TextMate

        val annotatedString = runBlocking { highlighter.highlight(code, languageName).last() }

        assertTrue("PHP code should be highlighted by TextMate", annotatedString.spanStyles.size > 1)
    }

    @Test
    fun `should return plain text for unknown language`() {
        val code = "some random text"
        val languageName = "not-a-real-language"

        val annotatedString = runBlocking { highlighter.highlight(code, languageName).last() }

        assertTrue("Unknown language should not be highlighted", annotatedString.spanStyles.isEmpty())
    }

    @Test
    fun `should return plain text for blank language name`() {
        val code = "public class Test {}"

        val annotatedString = runBlocking { highlighter.highlight(code, "").last() }

        assertEquals(code, annotatedString.text)
        assertTrue(annotatedString.spanStyles.isEmpty())
    }
}
