// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.markdown.utils

import com.intellij.openapi.util.NlsSafe
import org.intellij.markdown.IElementType
import org.intellij.markdown.ast.ASTNode
import org.intellij.markdown.flavours.MarkdownFlavourDescriptor
import org.intellij.markdown.flavours.gfm.GFMFlavourDescriptor
import org.intellij.markdown.html.HtmlGenerator
import org.intellij.markdown.parser.CancellationToken
import org.intellij.markdown.parser.LinkMap
import org.intellij.markdown.parser.MarkdownParser
import org.jetbrains.annotations.ApiStatus
import java.net.URI

@ApiStatus.Internal
class MarkdownToHtmlConverter(
  private val flavourDescriptor: MarkdownFlavourDescriptor
) {
  @NlsSafe
  fun convertMarkdownToHtml(@NlsSafe markdownText: String, server: String? = null): String {
    // Typed as a CharSequence to reach the parser's supported overloads; the `String` ones are deprecated.
    val text: CharSequence = markdownText
    val parsedTree = MarkdownParser(flavourDescriptor, cancellationToken = CancellationToken.NonCancellable)
      .buildMarkdownTreeFromString(text)
    val providers = flavourDescriptor.createHtmlGeneratingProviders(
      linkMap = LinkMap.buildLinkMap(parsedTree, markdownText),
      baseURI = server?.let { URI(it) }
    )

    return HtmlGenerator(markdownText, parsedTree, providers, false).generateHtml()
  }
}

// https://github.com/JetBrains/markdown/issues/72
private val embeddedHtmlType = IElementType("ROOT")

/**
 * Parses [markdownText] into the GFM tree [convertMarkdownToHtml] renders from.
 *
 * Exposed so that a caller which has to agree with the rendered output about what the Markdown *is* — where a
 * fenced block starts, whether it has been closed, what its info string says — can read the same tree instead
 * of keeping a second grammar of its own. Two grammars disagreeing about a fence is worse than an ordinary
 * rendering difference when one of them decides that a block is replaced by a stateful component.
 *
 * Pass `parseInlines = false` when only the block structure matters. Inline parsing is the bulk of the work
 * and the part that degrades on pathological input, so a block-level caller should skip it.
 */
@ApiStatus.Internal
fun parseGfmMarkdownToAst(
  @NlsSafe markdownText: CharSequence,
  flavour: MarkdownFlavourDescriptor = GFMFlavourDescriptor(),
  parseInlines: Boolean = true,
): ASTNode = MarkdownParser(flavour, cancellationToken = CancellationToken.NonCancellable)
  .parse(embeddedHtmlType, markdownText, parseInlines)

fun convertMarkdownToHtml(@NlsSafe markdownText: String): @NlsSafe String {
  val flavour = GFMFlavourDescriptor()
  return HtmlGenerator(markdownText, parseGfmMarkdownToAst(markdownText, flavour), flavour).generateHtml()
}