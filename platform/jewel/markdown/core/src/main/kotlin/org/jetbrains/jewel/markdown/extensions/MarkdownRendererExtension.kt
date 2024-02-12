package org.jetbrains.jewel.markdown.extensions

import org.jetbrains.jewel.foundation.ExperimentalJewelApi

/** An extension for the Jewel Markdown rendering engine. */
@ExperimentalJewelApi
public interface MarkdownRendererExtension {

    /**
     * An extension for
     * [`MarkdownBlockRenderer`][org.jetbrains.jewel.markdown.rendering.MarkdownBlockRenderer]
     * that will render a supported
     * [`Extension`][org.jetbrains.jewel.markdown.MarkdownBlock.Extension] into
     * a native Jewel UI.
     */
    public val blockRenderer: MarkdownBlockRendererExtension
}
