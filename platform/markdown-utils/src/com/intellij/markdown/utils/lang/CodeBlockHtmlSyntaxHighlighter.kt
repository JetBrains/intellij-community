// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.markdown.utils.lang

import com.intellij.lang.Language
import com.intellij.markdown.utils.lang.HtmlSyntaxHighlighter.Companion.colorHtmlChunk
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.util.text.HtmlChunk

class CodeBlockHtmlSyntaxHighlighter(
  private val project: Project?
) : HtmlSyntaxHighlighter {
  override fun color(language: String?, @NlsSafe rawContent: String): HtmlChunk {
    return findRegisteredLanguage(language)?.let {
      colorHtmlChunk(project, it, rawContent).wrapWith("pre")
    } ?: HtmlChunk.text(rawContent)
  }

  private fun findRegisteredLanguage(language: String?): Language? = Language.getRegisteredLanguages()
    .singleOrNull { registeredLanguage ->
      registeredLanguage.id.lowercase() == language?.lowercase()
    }
}