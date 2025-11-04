// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jewel.markdown.processing.html

import org.jetbrains.annotations.ApiStatus
import org.jetbrains.jewel.foundation.ExperimentalJewelApi
import org.jetbrains.jewel.markdown.InlineMarkdown
import org.jetbrains.jewel.markdown.MarkdownBlock
import org.jetbrains.jewel.markdown.WithInlineMarkdown
import org.jetbrains.jewel.markdown.processing.MarkdownProcessor
import org.jetbrains.jewel.markdown.scrolling.ScrollingSynchronizer

/**
 * Defines a mechanism for converting HTML elements their child elements, and inline elements into supported Markdown
 * elements (blocks or inlines).
 */
@ApiStatus.Experimental
@ExperimentalJewelApi
public interface HtmlElementConverter {
    /**
     * Converts an HTML element into a Markdown block by applying child element and inline element conversion logic.
     *
     * Implement this method if the HTML element should be converted into a Markdown block.
     *
     * @param htmlElement the HTML element to be converted into a Markdown block
     * @param convertChildren a lambda to convert child elements of the given HTML element into a list of Markdown
     *   blocks
     * @param convertInlines a function to convert inline elements into a list of inline Markdown elements
     * @return the resulting Markdown block or `null` if the conversion is not applicable for the given element
     */
    @ApiStatus.Experimental
    @ExperimentalJewelApi
    public fun convert(
        htmlElement: MarkdownHtmlNode.Element,
        convertChildren: MarkdownHtmlNode.Element.() -> List<MarkdownBlock>,
        convertInlines: (List<MarkdownHtmlNode>) -> List<InlineMarkdown>,
    ): MarkdownBlock? = null

    /**
     * Converts a Markdown HTML node into a list of inline Markdown elements by applying the provided conversion logic
     * for its inline elements.
     *
     * Use this method if the HTML node should be converted into a list of inline Markdown elements.
     *
     * @param element the [MarkdownHtmlNode] to be converted into a list of inline Markdown elements
     * @param convertSubInlines a lambda function to handle the conversion of the child inline elements of the given
     *   node
     * @return the resulting list of converted inline Markdown elements, or `null` if the conversion is not applicable
     */
    @ApiStatus.Experimental
    @ExperimentalJewelApi
    public fun convertInlines(
        element: MarkdownHtmlNode,
        convertSubInlines: () -> List<InlineMarkdown>,
    ): List<InlineMarkdown>? = null
}

internal class MarkdownHtmlConverter {
    private val inlinesConverter = MarkdownHtmlInlinesConverter()

    fun convert(
        processor: MarkdownProcessor,
        htmlElement: MarkdownHtmlNode,
        transform: (MarkdownBlock, IntRange) -> MarkdownBlock,
    ): MarkdownBlock? {
        return when (htmlElement) {
            is MarkdownHtmlNode.Text -> {
                processor.processRawMarkdown(htmlElement.htmlContent).firstOrNull()
            }
            is MarkdownHtmlNode.Element -> {
                val converter = provideConverter(processor, htmlElement.tag) ?: return null
                val convertedBlock =
                    converter.convert(
                        htmlElement,
                        { convertChildren(processor, transform) },
                        { convert(processor, htmlElementsToInlines(processor, it)) },
                    ) ?: return null
                val transformedBlock = transform(convertedBlock, htmlElement.lineRange)

                return if (htmlElement.attributes.isEmpty()) {
                    transformedBlock
                } else {
                    transform(
                        MarkdownBlock.HtmlBlockWithAttributes(transformedBlock, htmlElement.attributes),
                        htmlElement.lineRange,
                    )
                }
            }
        }
    }

    private fun MarkdownHtmlNode.Element.convertChildren(
        processor: MarkdownProcessor,
        transform: (MarkdownBlock, IntRange) -> MarkdownBlock,
    ): List<MarkdownBlock> {
        // Some HTML elements may make up a block (e.g. nested list), but some can be pure inlines.
        // For example, `<li><p>Hello</p></li>` is a block inside ListItem, but `<li><b>Hello<b></li>` is not.
        // What's worse, `<li>Pay attention to <b>this</b>:<ol>...</ol></li>` combines both inlines and blocks.
        // The idea here is to split the children into sublists of elements convertible to inline lists,
        // separated by markdown blocks.
        val blocks = mutableListOf<MarkdownBlock>()
        val currentInlineHtmlElements = mutableListOf<MarkdownHtmlNode>()
        var currentLine = lineRange.first
        for (child in children) {
            if (child is MarkdownHtmlNode.Element && child.isBlock) {
                flushHtmlElementsToMarkdownBlocks(currentInlineHtmlElements, processor) { block ->
                        transform(block, currentLine until child.lineRange.first)
                    }
                    ?.let { blocks.add(it) }

                convert(processor, child, transform)?.let { blocks.add(it) }

                currentLine = child.lineRange.last
            } else {
                currentInlineHtmlElements.add(child)
            }
        }
        flushHtmlElementsToMarkdownBlocks(currentInlineHtmlElements, processor) { block ->
                transform(block, currentLine..lineRange.last)
            }
            ?.let { blocks.add(it) }
        return blocks
    }

    private fun flushHtmlElementsToMarkdownBlocks(
        currentInlineHtmlElements: MutableList<MarkdownHtmlNode>,
        processor: MarkdownProcessor,
        transform: (MarkdownBlock) -> MarkdownBlock,
    ): MarkdownBlock? {
        if (currentInlineHtmlElements.isEmpty()) return null
        val inlines = htmlElementsToInlines(processor, currentInlineHtmlElements)
        currentInlineHtmlElements.clear()
        return transform(MarkdownBlock.Paragraph(convert(processor, inlines)))
    }

    fun convert(processor: MarkdownProcessor, inlines: List<InlineMarkdown>): List<InlineMarkdown> =
        inlinesConverter.convert(processor, inlines)

    private fun htmlElementsToInlines(
        processor: MarkdownProcessor,
        htmlElements: List<MarkdownHtmlNode>,
    ): List<InlineMarkdown> = htmlElements.joinToString("") { it.htmlContent }.trim().processToInlines(processor)

    private fun String.processToInlines(processor: MarkdownProcessor): List<InlineMarkdown> =
        processor.processRawMarkdown(this).flatMap { markdownBlock ->
            val ogBlock =
                if (markdownBlock is ScrollingSynchronizer.LocatableMarkdownBlock) {
                    markdownBlock.originalBlock
                } else {
                    markdownBlock
                }
            (ogBlock as? WithInlineMarkdown)?.inlineContent.orEmpty()
        }

    private fun provideConverter(processor: MarkdownProcessor, tag: String): HtmlElementConverter? =
        converters[tag] ?: processor.provideExtensionHtmlElementConverterFor(tag)
}
