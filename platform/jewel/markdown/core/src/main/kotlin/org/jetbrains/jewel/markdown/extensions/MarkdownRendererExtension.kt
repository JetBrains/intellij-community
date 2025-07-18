package org.jetbrains.jewel.markdown.extensions

import org.jetbrains.annotations.ApiStatus
import org.jetbrains.jewel.foundation.ExperimentalJewelApi

/** An extension for the Jewel Markdown rendering engine. */
@ApiStatus.Experimental
@ExperimentalJewelApi
public interface MarkdownRendererExtension {
    /**
     * An extension for [`MarkdownBlockRenderer`][org.jetbrains.jewel.markdown.rendering.MarkdownBlockRenderer] that
     * will render a supported [`CustomBlock`][org.jetbrains.jewel.markdown.MarkdownBlock.CustomBlock] into a native
     * Jewel UI.
     *
     * Can be null if this extension doesn't support rendering blocks.
     */
    public val blockRenderer: MarkdownBlockRendererExtension?
        get() = null

    /**
     * An extension for [`InlineMarkdownRenderer`][org.jetbrains.jewel.markdown.rendering.InlineMarkdownRenderer] that
     * will render supported [org.jetbrains.jewel.markdown.InlineMarkdown.CustomDelimitedNode]s into an annotated
     * string.
     *
     * Can be null if this extension doesn't support rendering delimited inline nodes.
     */
    public val delimitedInlineRenderer: MarkdownDelimitedInlineRendererExtension?
        get() = null

    /**
     * An extension for handling the rendering of image elements. Can be null if no custom image rendering is provided.
     */
    public val imageRendererExtension: ImageRendererExtension?
        get() = null
}
