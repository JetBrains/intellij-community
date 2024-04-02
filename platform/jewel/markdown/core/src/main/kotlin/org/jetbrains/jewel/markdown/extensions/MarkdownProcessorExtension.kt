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
     * syntax represented by this extension instance. Null in the case where
     * parsing is already handled by an existing [org.commonmark.parser.Parser].
     */
    public val parserExtension: ParserExtension?

    /**
     * A CommonMark [TextContentRendererExtension] that will be used to render
     * the text content of the CommonMark [CustomBlock] produced by the
     * [parserExtension]. Null in the case where rendering is already
     * handled by an existing [org.commonmark.renderer.Renderer].
     */
    public val textRendererExtension: TextContentRendererExtension?

    /**
     * An extension for
     * [`MarkdownParser`][org.jetbrains.jewel.markdown.parsing.MarkdownParser]
     * that will transform a supported [CustomBlock] into the corresponding
     * [MarkdownBlock.CustomBlock]. Null in the case where processing
     * is already be handled by [org.jetbrains.jewel.markdown.processing.MarkdownProcessor]
     * or another [org.jetbrains.jewel.markdown.extensions.MarkdownProcessorExtension].
     */
    public val processorExtension: MarkdownBlockProcessorExtension?
}
