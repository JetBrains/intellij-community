// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.collaboration.markdown

import com.intellij.openapi.util.NlsSafe
import org.intellij.markdown.flavours.MarkdownFlavourDescriptor
import org.intellij.markdown.html.HtmlGenerator
import org.intellij.markdown.parser.LinkMap
import org.intellij.markdown.parser.MarkdownParser
import java.net.URI

class MarkdownToHtmlConverter(
  private val flavourDescriptor: MarkdownFlavourDescriptor
) {
  @NlsSafe
  fun convertMarkdownToHtml(@NlsSafe markdownText: String, server: String? = null): String {
    val parsedTree = MarkdownParser(flavourDescriptor).buildMarkdownTreeFromString(markdownText)
    val providers = flavourDescriptor.createHtmlGeneratingProviders(
      linkMap = LinkMap.buildLinkMap(parsedTree, markdownText),
      baseURI = server?.let { URI(it) }
    )

    return HtmlGenerator(markdownText, parsedTree, providers, false).generateHtml()
  }
}