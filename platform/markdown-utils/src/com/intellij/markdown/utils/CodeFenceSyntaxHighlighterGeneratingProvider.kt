// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.markdown.utils

import com.intellij.markdown.utils.lang.HtmlSyntaxHighlighter
import com.intellij.openapi.util.text.HtmlBuilder
import com.intellij.openapi.util.text.HtmlChunk
import org.intellij.markdown.MarkdownTokenTypes
import org.intellij.markdown.ast.ASTNode
import org.intellij.markdown.ast.getTextInNode
import org.intellij.markdown.html.GeneratingProvider
import org.intellij.markdown.html.HtmlGenerator
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
class CodeFenceSyntaxHighlighterGeneratingProvider(
  private val htmlSyntaxHighlighter: HtmlSyntaxHighlighter
) : GeneratingProvider {

  override fun processNode(visitor: HtmlGenerator.HtmlGeneratingVisitor, text: String, node: ASTNode) {
    var languageCodeBlock: String? = null
    val codeFenceRawContent = StringBuilder()

    val iterator = node.children.iterator()
    while (iterator.hasNext()) {
      val child = iterator.next()
      when (child.type) {
        MarkdownTokenTypes.FENCE_LANG -> {
          languageCodeBlock = HtmlGenerator.leafText(text, child).toString().trim()
          iterator.next() // skip first EOL after language
        }
        MarkdownTokenTypes.CODE_FENCE_CONTENT, MarkdownTokenTypes.EOL -> {
          val textInNode = child.getTextInNode(text)
          codeFenceRawContent.append(HtmlGenerator.trimIndents(textInNode, 0))
        }
        MarkdownTokenTypes.CODE_FENCE_END -> break
      }
    }

    val coloredContent = htmlSyntaxHighlighter.color(languageCodeBlock, codeFenceRawContent.toString())

    val codeHtmlChunk = HtmlChunk.tag("code").let {
      if (languageCodeBlock == null) it
      else it.setClass("language-${languageCodeBlock.split(" ").joinToString(separator = "-")}")
    }

    val html = HtmlBuilder().append(coloredContent).wrapWith(codeHtmlChunk)
    visitor.consumeHtml(html.toString())
  }
}