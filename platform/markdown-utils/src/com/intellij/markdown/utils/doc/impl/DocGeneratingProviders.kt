// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.markdown.utils.doc.impl

import com.intellij.lang.Language
import com.intellij.lang.documentation.QuickDocHighlightingHelper
import com.intellij.lang.documentation.QuickDocHighlightingHelper.guessLanguage
import com.intellij.markdown.utils.doc.DocMarkdownToHtmlConverter
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.text.StringUtil
import com.intellij.util.containers.CollectionFactory
import org.intellij.markdown.MarkdownTokenTypes
import org.intellij.markdown.ast.ASTNode
import org.intellij.markdown.ast.LeafASTNode
import org.intellij.markdown.ast.accept
import org.intellij.markdown.ast.getTextInNode
import org.intellij.markdown.html.GeneratingProvider
import org.intellij.markdown.html.HtmlGenerator
import org.intellij.markdown.html.TrimmingInlineHolderProvider

private val TAG_REPLACE_MAP = CollectionFactory.createCharSequenceMap<String>(false).also {
  it["div"] = "span"
  it["em"] = "i"
  it["strong"] = "b"
}

internal class DocSanitizingTagGeneratingProvider : GeneratingProvider {
  override fun processNode(visitor: HtmlGenerator.HtmlGeneratingVisitor, text: String, node: ASTNode) {
    val nodeText = node.getTextInNode(text)
    if (nodeText.contentEquals("</p>", true)) return
    val matcher = DocMarkdownToHtmlConverter.TAG_PATTERN.matcher(nodeText)
    if (matcher.matches()) {
      val tagName = matcher.group(1)
      val replaceWith = TAG_REPLACE_MAP[tagName]
      if (replaceWith != null) {
        visitor.consumeHtml(nodeText.subSequence(0, matcher.start(1)))
        visitor.consumeHtml(replaceWith)
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
      .joinToString(separator = "") { it.getTextInNode(text) }
    visitor.consumeHtml(QuickDocHighlightingHelper.getStyledInlineCode(project, defaultLanguage, output))
  }
}

internal class DocParagraphGeneratingProvider : TrimmingInlineHolderProvider() {
  override fun openTag(visitor: HtmlGenerator.HtmlGeneratingVisitor, text: String, node: ASTNode) {
    visitor.consumeTagOpen(node, "p")
  }

  override fun closeTag(visitor: HtmlGenerator.HtmlGeneratingVisitor, text: String, node: ASTNode) {
    visitor.consumeTagClose("p")
  }

  override fun processNode(visitor: HtmlGenerator.HtmlGeneratingVisitor, text: String, node: ASTNode) {
    val childrenToRender = childrenToRender(node)
    if (childrenToRender.isEmpty()) return

    openTag(visitor, text, node)

    for (child in childrenToRender(node)) {
      if (child is LeafASTNode) {
        visitor.visitLeaf(child)
      } else {
        child.accept(visitor)
      }
    }

    closeTag(visitor, text, node)
  }
}