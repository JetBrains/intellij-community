// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.collaboration.lang

import com.intellij.lang.Language
import com.intellij.openapi.editor.colors.EditorColorsManager
import com.intellij.openapi.fileTypes.SyntaxHighlighterFactory
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.text.HtmlChunk
import com.intellij.testFramework.LightVirtualFile
import java.awt.Color

interface HtmlSyntaxHighlighter {
  fun color(language: String?, rawContent: String): HtmlChunk

  companion object {
    fun parseContent(
      project: Project?,
      language: Language,
      text: String,
      collector: (String, IntRange, Color?) -> Unit
    ) {
      val file = LightVirtualFile("markdown_temp", text)
      val highlighter = SyntaxHighlighterFactory.getSyntaxHighlighter(language, project, file)
      val colorScheme = EditorColorsManager.getInstance().globalScheme

      val lexer = highlighter.highlightingLexer
      lexer.start(text)

      while (lexer.tokenType != null) {
        val type = lexer.tokenType
        val highlights = highlighter.getTokenHighlights(type).lastOrNull()
        val color = highlights?.let {
          colorScheme.getAttributes(it)?.foregroundColor
        } ?: highlights?.defaultAttributes?.foregroundColor

        collector(lexer.tokenText, lexer.tokenStart..lexer.tokenEnd, color)
        lexer.advance()
      }
    }
  }
}