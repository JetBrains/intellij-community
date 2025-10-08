// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jewel.markdown.processing.html

import org.jetbrains.annotations.ApiStatus
import org.jetbrains.jewel.foundation.ExperimentalJewelApi
import org.jetbrains.jewel.markdown.InlineMarkdown
import org.jetbrains.jewel.markdown.MarkdownBlock
import org.jetbrains.jewel.markdown.WithInlineMarkdown
import org.jetbrains.jewel.markdown.processing.MarkdownProcessor
import org.jetbrains.jewel.markdown.scrolling.ScrollingSynchronizer

@ApiStatus.Experimental
@ExperimentalJewelApi
public interface ElementConverter {
    // TODO separate methods into two interfaces? One converter doesn't need to support both.
    @ApiStatus.Experimental
    @ExperimentalJewelApi
    public fun convert(
        htmlElement: MarkdownHtmlElement.Element,
        convertChildren: MarkdownHtmlElement.Element.(transformChildren: Boolean) -> List<MarkdownBlock>,
        convertInlines: (List<MarkdownHtmlElement>) -> List<InlineMarkdown>,
    ): MarkdownBlock?
}

@ApiStatus.Experimental
@ExperimentalJewelApi
public sealed interface MarkdownHtmlElementConversionResult {
    @ApiStatus.Experimental @ExperimentalJewelApi public object None : MarkdownHtmlElementConversionResult

    @ApiStatus.Experimental
    @ExperimentalJewelApi
    public class Block(public val block: MarkdownBlock) : MarkdownHtmlElementConversionResult

    @ApiStatus.Experimental
    @ExperimentalJewelApi
    public class Inlines(public val inlines: List<InlineMarkdown>) : MarkdownHtmlElementConversionResult
}

internal class MarkdownHtmlConverter {

    private val inlinesConverter = MarkdownHtmlInlinesConverter()

    fun convert(
        processor: MarkdownProcessor,
        htmlElement: MarkdownHtmlElement,
        transform: (MarkdownBlock, IntRange) -> MarkdownBlock,
    ): MarkdownBlock? {
        return when (htmlElement) {
            is MarkdownHtmlElement.Text -> {
                processor.processRawMarkdown(htmlElement.htmlContent).firstOrNull()
            }
            is MarkdownHtmlElement.Element -> {
                val converter = provideConverter(processor, htmlElement.tag) ?: return null
                val block =
                    converter.convert(
                        htmlElement,
                        { transformChildren -> convertChildren(processor, transform) },
                        { convert(htmlElementsToInlines(processor, it)) },
                    ) ?: return null
                transform(block, htmlElement.lineRange)
            }
        }
    }

    private fun MarkdownHtmlElement.Element.convertChildren(
        processor: MarkdownProcessor,
        transform: (MarkdownBlock, IntRange) -> MarkdownBlock,
    ): List<MarkdownBlock> {
        // Some HTML elements may make up a block (e.g. nested list), but some can be pure inlines.
        // For example, `<li><p>Hello</p></li>` is a block inside ListItem, but `<li><b>Hello<b></li>` is not.
        // What's worse, `<li>Pay attention to <b>this</b>:<ol>...</ol></li>` combines both inlines and blocks.
        // The idea here is to split the children into sublists of elements convertible to inline lists,
        // separated by markdown blocks.
        val blocks = mutableListOf<MarkdownBlock>()
        val currentInlineHtmlElements = mutableListOf<MarkdownHtmlElement>()
        var currentLine = lineRange.first
        for (child in children) {
            if (child is MarkdownHtmlElement.Element && child.isBlock) {
                flushHtmlElementsToMarkdownBlocks(currentInlineHtmlElements, processor) { block ->
                        transform(block, currentLine until child.lineRange.first)
                    }
                    ?.let { blocks.add(it) }

                convert(processor, child, transform)?.let { blocks.add(it) }

                currentLine = child.lineRange.last + 1
            } else {
                currentInlineHtmlElements.add(child)
            }
        }
        flushHtmlElementsToMarkdownBlocks(currentInlineHtmlElements, processor) { block ->
                transform(block, currentLine until lineRange.last)
            }
            ?.let { blocks.add(it) }
        return blocks
    }

    private fun flushHtmlElementsToMarkdownBlocks(
        currentInlineHtmlElements: MutableList<MarkdownHtmlElement>,
        processor: MarkdownProcessor,
        transform: (MarkdownBlock) -> MarkdownBlock,
    ): MarkdownBlock? {
        if (currentInlineHtmlElements.isEmpty()) return null
        val inlines = htmlElementsToInlines(processor, currentInlineHtmlElements)
        currentInlineHtmlElements.clear()
        return transform(MarkdownBlock.Paragraph(convert(inlines)))
    }

    fun convert(inlines: List<InlineMarkdown>): List<InlineMarkdown> = inlinesConverter.convert(inlines)

    private fun htmlElementsToInlines(
        processor: MarkdownProcessor,
        htmlElements: List<MarkdownHtmlElement>,
    ): List<InlineMarkdown> = htmlElements.joinToString("") { it.htmlContent }.trim().processToInlines(processor)

    private fun String.processToInlines(processor: MarkdownProcessor): List<InlineMarkdown> =
        processor.processRawMarkdown(this).flatMap { markdownBlock ->
            val ogBlock =
                if (markdownBlock is ScrollingSynchronizer.LocatableMarkdownBlock) {
                    markdownBlock.originalBlock
                } else markdownBlock
            (ogBlock as? WithInlineMarkdown)?.inlineContent.orEmpty()
        }

    private fun provideConverter(processor: MarkdownProcessor, tag: String): ElementConverter? =
        converters[tag] ?: processor.htmlConverterExtensions.firstNotNullOfOrNull { it.provideConverter(tag) }
}
