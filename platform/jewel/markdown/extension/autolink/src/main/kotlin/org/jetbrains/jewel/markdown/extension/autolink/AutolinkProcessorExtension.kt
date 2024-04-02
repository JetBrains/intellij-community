package org.jetbrains.jewel.markdown.extension.autolink

import org.commonmark.ext.autolink.AutolinkExtension
import org.commonmark.parser.Parser.ParserExtension
import org.commonmark.renderer.text.TextContentRenderer
import org.jetbrains.jewel.markdown.extensions.MarkdownBlockProcessorExtension
import org.jetbrains.jewel.markdown.extensions.MarkdownProcessorExtension

public object AutolinkProcessorExtension : MarkdownProcessorExtension {
    override val parserExtension: ParserExtension
        get() = AutolinkExtension.create() as ParserExtension

    /**
     * Rendering and processing is already handled by [org.jetbrains.jewel.markdown.rendering.DefaultInlineMarkdownRenderer]
     */
    override val textRendererExtension: TextContentRenderer.TextContentRendererExtension?
        get() = null
    override val processorExtension: MarkdownBlockProcessorExtension?
        get() = null
}
