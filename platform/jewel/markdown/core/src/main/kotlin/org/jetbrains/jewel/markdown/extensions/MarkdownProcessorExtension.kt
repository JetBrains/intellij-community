package org.jetbrains.jewel.markdown.extensions

import org.commonmark.parser.Parser.ParserExtension
import org.commonmark.renderer.text.TextContentRenderer.TextContentRendererExtension
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.jewel.foundation.ExperimentalJewelApi

/** An extension for the Jewel Markdown processing engine. */
@ApiStatus.Experimental
@ExperimentalJewelApi
public interface MarkdownProcessorExtension {
    /**
     * A CommonMark [ParserExtension] that will be used to parse the extended syntax represented by this extension
     * instance.
     *
     * Can be null if all required parsing is already handled by an existing [org.commonmark.parser.Parser].
     */
    public val parserExtension: ParserExtension?
        get() = null

    /**
     * A CommonMark [TextContentRendererExtension] that will be used to render the text content of the CommonMark
     * [org.commonmark.node.CustomBlock] produced by the [parserExtension].
     *
     * Can be null if all required processing is already handled by an existing [org.commonmark.renderer.Renderer].
     */
    public val textRendererExtension: TextContentRendererExtension?
        get() = null

    /**
     * An extension for [`MarkdownProcessor`][org.jetbrains.jewel.markdown.processing.MarkdownProcessor] that will
     * transform a supported [org.commonmark.node.CustomBlock] into the corresponding
     * [org.jetbrains.jewel.markdown.MarkdownBlock.CustomBlock].
     *
     * Can be null if all required processing is already handled by
     * [org.jetbrains.jewel.markdown.processing.MarkdownProcessor] or another
     * [org.jetbrains.jewel.markdown.extensions.MarkdownProcessorExtension].
     */
    public val blockProcessorExtension: MarkdownBlockProcessorExtension?
        get() = null

    /**
     * A [MarkdownDelimitedInlineProcessorExtension] that will transform a supported [org.commonmark.node.Delimited]
     * inline node into the corresponding [org.jetbrains.jewel.markdown.InlineMarkdown.CustomDelimitedNode].
     *
     * Can be null if this extension does not handle custom delimited inline nodes.
     */
    public val delimitedInlineProcessorExtension: MarkdownDelimitedInlineProcessorExtension?
        get() = null
}
