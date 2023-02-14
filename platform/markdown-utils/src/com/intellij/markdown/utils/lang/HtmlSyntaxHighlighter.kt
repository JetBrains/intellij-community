// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.markdown.utils.lang

import com.intellij.lang.Language
import com.intellij.openapi.editor.colors.EditorColorsManager
import com.intellij.openapi.fileTypes.SyntaxHighlighterFactory
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.text.HtmlBuilder
import com.intellij.openapi.util.text.HtmlChunk
import com.intellij.testFramework.LightVirtualFile
import com.intellij.ui.ColorUtil
import java.awt.Color

interface HtmlSyntaxHighlighter {
  fun color(language: String?, rawContent: String): HtmlChunk

  companion object {
    fun parseContent(project: Project?,
                     language: Language,
                     text: String,
                     collector: (String, IntRange, Color?) -> Unit) {
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

    fun colorHtmlChunk(project: Project?, language: Language, rawContent: String): HtmlChunk {
      val html = HtmlBuilder()
      parseContent(project, language, rawContent) { content, _, color ->
        html.append(
          if (color != null) HtmlChunk.span("color:${ColorUtil.toHtmlColor(color)}").addText(content)
          else HtmlChunk.text(content)
        )
      }

      return html.toFragment()
    }
  }
}