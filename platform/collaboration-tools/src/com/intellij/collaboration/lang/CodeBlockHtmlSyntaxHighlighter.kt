// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.collaboration.lang

import com.intellij.collaboration.lang.HtmlSyntaxHighlighter.Companion.parseContent
import com.intellij.lang.Language
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.util.text.HtmlBuilder
import com.intellij.openapi.util.text.HtmlChunk
import com.intellij.ui.ColorUtil

class CodeBlockHtmlSyntaxHighlighter(
  private val project: Project?
) : HtmlSyntaxHighlighter {
  override fun color(language: String?, @NlsSafe rawContent: String): HtmlChunk {
    return findRegisteredLanguage(language)?.let {
      val html = HtmlBuilder()
      parseContent(project, it, rawContent) { content, _, color ->
        html.append(
          if (color != null) HtmlChunk.span("color:${ColorUtil.toHtmlColor(color)}").addText(content)
          else HtmlChunk.text(content)
        )
      }
      html.wrapWith("pre")
    } ?: HtmlChunk.text(rawContent)
  }

  private fun findRegisteredLanguage(language: String?): Language? = Language.getRegisteredLanguages()
    .singleOrNull { registeredLanguage ->
      registeredLanguage.id.lowercase() == language?.lowercase()
    }
}