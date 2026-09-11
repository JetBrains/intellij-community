// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.analysisignore.lang

import com.intellij.ide.analysisignore.ANALYSIS_IGNORE_FILE_NAME
import com.intellij.ide.highlighter.HighlighterFactory
import com.intellij.openapi.editor.DefaultLanguageHighlighterColors
import com.intellij.openapi.editor.colors.TextAttributesKey
import com.intellij.openapi.fileTypes.FileTypeManager
import com.intellij.openapi.fileTypes.SyntaxHighlighterFactory
import com.intellij.testFramework.junit5.TestApplication
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test

@TestApplication
class AnalysisIgnoreHighlightingTest {

  @Test
  fun `the file name selects the file type`() {
    val fileType = FileTypeManager.getInstance().getFileTypeByFileName(ANALYSIS_IGNORE_FILE_NAME)
    assertSame(AnalysisIgnoreFileType, fileType)
    assertSame(AnalysisIgnoreLanguage, AnalysisIgnoreFileType.language)
  }

  @Test
  fun `a comment gets the line comment color and a pattern gets none`() {
    val highlighter = SyntaxHighlighterFactory.getSyntaxHighlighter(AnalysisIgnoreLanguage, null, null)
    assertEquals(listOf(DefaultLanguageHighlighterColors.LINE_COMMENT),
                 highlighter.getTokenHighlights(AnalysisIgnoreElementTypes.COMMENT).toList())
    assertEquals(emptyList<TextAttributesKey>(), highlighter.getTokenHighlights(AnalysisIgnoreElementTypes.PATTERN).toList())
  }

  @Test
  fun `the editor highlighter colors the comment lines only`() {
    val text = "# a comment\n  # a pattern\nbuild/\n"
    val highlighter = HighlighterFactory.createHighlighter(null, ANALYSIS_IGNORE_FILE_NAME)
    highlighter.setText(text)

    val iterator = highlighter.createIterator(0)
    val actual = buildList {
      while (!iterator.atEnd()) {
        val tokenText = text.substring(iterator.start, iterator.end).replace("\n", "\\n")
        add("${iterator.tokenType} ('$tokenText') ${iterator.textAttributesKeys.map { it.externalName }}")
        iterator.advance()
      }
    }

    assertEquals(
      listOf(
        "COMMENT ('# a comment') [DEFAULT_LINE_COMMENT]",
        "WHITE_SPACE ('\\n') []",
        "PATTERN ('  # a pattern') []",
        "WHITE_SPACE ('\\n') []",
        "PATTERN ('build/') []",
        "WHITE_SPACE ('\\n') []",
      ),
      actual,
    )
  }
}
