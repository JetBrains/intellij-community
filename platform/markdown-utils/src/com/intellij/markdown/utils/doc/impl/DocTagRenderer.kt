// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.markdown.utils.doc.impl

import com.intellij.markdown.utils.doc.DocMarkdownToHtmlConverter
import org.intellij.markdown.MarkdownTokenTypes
import org.intellij.markdown.ast.ASTNode
import org.intellij.markdown.ast.getTextInNode
import org.intellij.markdown.html.DUMMY_ATTRIBUTES_CUSTOMIZER
import org.intellij.markdown.html.HtmlGenerator

internal class DocTagRenderer(private val wholeText: String)
  : HtmlGenerator.DefaultTagRenderer(DUMMY_ATTRIBUTES_CUSTOMIZER, false) {

  override fun openTag(node: ASTNode, tagName: CharSequence,
                       vararg attributes: CharSequence?,
                       autoClose: Boolean): CharSequence {
    if (tagName.contentEquals("p", true)) {
      val first = node.children.firstOrNull()
      if (first != null && first.type === MarkdownTokenTypes.HTML_TAG) {
        val text = first.getTextInNode(wholeText)
        val matcher = DocMarkdownToHtmlConverter.TAG_PATTERN.matcher(text)
        if (matcher.matches()) {
          val nestedTag = matcher.group(1)
          if (DocMarkdownToHtmlConverter.ACCEPTABLE_BLOCK_TAGS.contains(nestedTag)) {
            return ""
          }
        }
      }
    }
    if (tagName.contentEquals("code", true) && node.type === MarkdownTokenTypes.CODE_FENCE_CONTENT) {
      return ""
    }
    return super.openTag(node, convertTag(tagName), *attributes, autoClose = autoClose)
  }

  override fun closeTag(tagName: CharSequence): CharSequence {
    if (tagName.contentEquals("p", true)) return ""
    return super.closeTag(convertTag(tagName))
  }

  private fun convertTag(tagName: CharSequence): CharSequence {
    if (tagName.contentEquals("strong", true)) {
      return "b"
    }
    else if (tagName.contentEquals("em", true)) {
      return "i"
    }
    return tagName
  }
}