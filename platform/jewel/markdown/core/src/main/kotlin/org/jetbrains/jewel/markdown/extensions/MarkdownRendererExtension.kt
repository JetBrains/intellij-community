package org.jetbrains.jewel.markdown.extensions

import org.jetbrains.jewel.foundation.ExperimentalJewelApi

/** An extension for the Jewel Markdown rendering engine. */
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
     * will render a supported [`CustomNode`][org.jetbrains.jewel.markdown.InlineMarkdown.CustomNode] into an annotated
     * string.
     *
     * Can be null if this extension doesn't support rendering inline nodes.
     */
    public val inlineRenderer: MarkdownInlineRendererExtension?
        get() = null
}
