// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.markdown.utils.doc.impl

import com.intellij.lang.Language
import com.intellij.lang.documentation.QuickDocHighlightingHelper
import com.intellij.lang.documentation.QuickDocHighlightingHelper.guessLanguage
import com.intellij.markdown.utils.doc.DocMarkdownToHtmlConverter
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.text.StringUtil
import org.intellij.markdown.MarkdownTokenTypes
import org.intellij.markdown.ast.ASTNode
import org.intellij.markdown.ast.getTextInNode
import org.intellij.markdown.html.GeneratingProvider
import org.intellij.markdown.html.HtmlGenerator

internal class DocSanitizingTagGeneratingProvider : GeneratingProvider {
  override fun processNode(visitor: HtmlGenerator.HtmlGeneratingVisitor, text: String, node: ASTNode) {
    val nodeText = node.getTextInNode(text)
    val matcher = DocMarkdownToHtmlConverter.TAG_PATTERN.matcher(nodeText)
    if (matcher.matches()) {
      val tagName = matcher.group(1)
      if (StringUtil.equalsIgnoreCase(tagName, "div")) {
        visitor.consumeHtml(nodeText.subSequence(0, matcher.start(1)))
        visitor.consumeHtml("span")
        visitor.consumeHtml(nodeText.subSequence(matcher.end(1), nodeText.length))
        return
      }
      if (DocMarkdownToHtmlConverter.ACCEPTABLE_TAGS.contains(tagName)) {
        visitor.consumeHtml(nodeText)
        return
      }
    }
    visitor.consumeHtml(StringUtil.escapeXmlEntities(nodeText.toString()))
  }
}

internal class DocCodeBlockGeneratingProvider(private val project: Project, private val defaultLanguage: Language?) : GeneratingProvider {
  override fun processNode(visitor: HtmlGenerator.HtmlGeneratingVisitor, text: String, node: ASTNode) {
    val contents = StringBuilder()
    var language: String? = null
    node.children.forEach { child ->
      when (child.type) {
        MarkdownTokenTypes.CODE_FENCE_CONTENT, MarkdownTokenTypes.CODE_LINE, MarkdownTokenTypes.EOL ->
          contents.append(child.getTextInNode(text))
        MarkdownTokenTypes.FENCE_LANG ->
          language = HtmlGenerator.leafText(text, child).toString().trim().takeWhile { !it.isWhitespace() }
      }
    }
    visitor.consumeHtml(QuickDocHighlightingHelper.getStyledCodeBlock(
      project, guessLanguage(language) ?: defaultLanguage, contents.toString()))
  }

}

internal class DocCodeSpanGeneratingProvider(private val project: Project, private val defaultLanguage: Language?) : GeneratingProvider {
  override fun processNode(visitor: HtmlGenerator.HtmlGeneratingVisitor, text: String, node: ASTNode) {
    val nodes = node.children.subList(1, node.children.size - 1)
    val output = nodes
      .filter { it.type != MarkdownTokenTypes.BLOCK_QUOTE }
      .joinToString(separator = "") { it.getTextInNode(text) }.trim()
    visitor.consumeHtml(QuickDocHighlightingHelper.getStyledInlineCode(project, defaultLanguage, output))
  }
}