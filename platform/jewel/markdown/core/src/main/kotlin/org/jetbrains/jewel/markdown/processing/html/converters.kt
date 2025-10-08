// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jewel.markdown.processing.html

import org.jetbrains.jewel.markdown.InlineMarkdown
import org.jetbrains.jewel.markdown.MarkdownBlock
import org.jetbrains.jewel.markdown.scrolling.ScrollingSynchronizer

private typealias Tag = String

/**
 * Refer to [org.commonmark.internal.HtmlBlockParser] for tags that commonmark translates to
 * [org.commonmark.node.HtmlBlock] instead of [org.commonmark.node.Paragraph] with inline HTML.
 */
internal val converters: Map<Tag, ElementConverter> =
    mapOf(
        "p" to ParagraphConverter,
        "li" to ListItemConverter,
        "ol" to OrderedListConverter,
        "ul" to UnorderedListConverter,
        "h1" to HeadingConverter(1),
        "h2" to HeadingConverter(2),
        "h3" to HeadingConverter(3),
        "h4" to HeadingConverter(4),
        "h5" to HeadingConverter(5),
        "h6" to HeadingConverter(6),
        "code" to MultilineCodeConverter,
        "pre" to MultilineCodeConverter,
    )

private object ParagraphConverter : ElementConverter {
    override fun convert(
        htmlElement: MarkdownHtmlElement.Element,
        convertChildren: MarkdownHtmlElement.Element.(transformChildren: Boolean) -> List<MarkdownBlock>,
        convertInlines: (List<MarkdownHtmlElement>) -> List<InlineMarkdown>,
    ): MarkdownBlock = MarkdownBlock.Paragraph(convertInlines(htmlElement.children))
}

private object ListItemConverter : ElementConverter {
    override fun convert(
        htmlElement: MarkdownHtmlElement.Element,
        convertChildren: MarkdownHtmlElement.Element.(transformChildren: Boolean) -> List<MarkdownBlock>,
        convertInlines: (List<MarkdownHtmlElement>) -> List<InlineMarkdown>,
    ): MarkdownBlock =
        // TODO support levels. Have to pass context as additional parameter?
        MarkdownBlock.ListItem(htmlElement.convertChildren(true), level = 0)
}

private abstract class ListConverter : ElementConverter {
    abstract fun convert(listItems: List<MarkdownBlock.ListItem>): MarkdownBlock

    final override fun convert(
        htmlElement: MarkdownHtmlElement.Element,
        convertChildren: MarkdownHtmlElement.Element.(transformChildren: Boolean) -> List<MarkdownBlock>,
        convertInlines: (List<MarkdownHtmlElement>) -> List<InlineMarkdown>,
    ): MarkdownBlock {
        val children = htmlElement.convertChildren(false)
        // TODO uhhh... I need to address that list's children shouldn't be wrapped in the first place
        val listItems =
            children.filterIsInstance<MarkdownBlock.ListItem>() +
                children
                    .filterIsInstance<ScrollingSynchronizer.LocatableMarkdownBlock>()
                    .map { it.originalBlock }
                    .filterIsInstance<MarkdownBlock.ListItem>()
        return convert(listItems)
    }
}

private object OrderedListConverter : ListConverter() {
    override fun convert(listItems: List<MarkdownBlock.ListItem>): MarkdownBlock =
        // TODO support levels. Have to pass context as additional parameter?
        MarkdownBlock.ListBlock.OrderedList(listItems, isTight = true, startFrom = 1, delimiter = ".")
}

private object UnorderedListConverter : ListConverter() {
    override fun convert(listItems: List<MarkdownBlock.ListItem>): MarkdownBlock =
        // TODO support levels. Have to pass context as additional parameter?
        MarkdownBlock.ListBlock.UnorderedList(listItems, isTight = true, marker = ".")
}

private class HeadingConverter(private val level: Int) : ElementConverter {
    override fun convert(
        htmlElement: MarkdownHtmlElement.Element,
        convertChildren: MarkdownHtmlElement.Element.(transformChildren: Boolean) -> List<MarkdownBlock>,
        convertInlines: (List<MarkdownHtmlElement>) -> List<InlineMarkdown>,
    ): MarkdownBlock = MarkdownBlock.Heading(convertInlines(htmlElement.children), level)
}

private object MultilineCodeConverter : ElementConverter {
    override fun convert(
        htmlElement: MarkdownHtmlElement.Element,
        convertChildren: MarkdownHtmlElement.Element.(transformChildren: Boolean) -> List<MarkdownBlock>,
        convertInlines: (List<MarkdownHtmlElement>) -> List<InlineMarkdown>,
    ): MarkdownBlock {
        // Capture the inner HTML literally (text + nested tags) so code contents are shown as-is.
        val raw = htmlElement.children.joinToString(separator = "") { it.htmlContent }
        // Preserve internal newlines and indentation, but drop surrounding blank lines caused by block formatting.
        val normalized = raw.trimIndent().trim('\n')
        return MarkdownBlock.CodeBlock.FencedCodeBlock(normalized, mimeType = null)
    }
}
