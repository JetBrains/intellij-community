package org.jetbrains.jewel.markdown.extensions

import org.commonmark.node.CustomBlock
import org.commonmark.parser.Parser.ParserExtension
import org.commonmark.renderer.text.TextContentRenderer.TextContentRendererExtension
import org.jetbrains.jewel.foundation.ExperimentalJewelApi
import org.jetbrains.jewel.markdown.MarkdownBlock

/** An extension for the Jewel Markdown processing engine. */
@ExperimentalJewelApi
public interface MarkdownProcessorExtension {

    /**
     * A CommonMark [ParserExtension] that will be used to parse the extended
     * syntax represented by this extension instance.
     */
    public val parserExtension: ParserExtension

    /**
     * A CommonMark [TextContentRendererExtension] that will be used to render
     * the text content of the CommonMark [CustomBlock] produced by the
     * [parserExtension].
     */
    public val textRendererExtension: TextContentRendererExtension

    /**
     * An extension for
     * [`MarkdownParser`][org.jetbrains.jewel.markdown.parsing.MarkdownParser]
     * that will transform a supported [CustomBlock] into the corresponding
     * [MarkdownBlock.CustomBlock].
     */
    public val processorExtension: MarkdownBlockProcessorExtension
}
