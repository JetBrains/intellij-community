package org.jetbrains.jewel.markdown.extensions.github.strikethrough

import org.commonmark.ext.gfm.strikethrough.StrikethroughExtension
import org.commonmark.parser.Parser.ParserExtension
import org.commonmark.renderer.text.TextContentRenderer
import org.jetbrains.jewel.markdown.extensions.MarkdownDelimitedInlineProcessorExtension
import org.jetbrains.jewel.markdown.extensions.MarkdownProcessorExtension

/**
 * Adds support for inline strikethrough to a [org.jetbrains.jewel.markdown.processing.MarkdownProcessor].
 *
 * Strikethrough is a GitHub Flavored Markdown extension, defined
 * [in the GFM specs](https://github.github.com/gfm/#strikethrough-extension-).
 *
 * @see StrikethroughExtension
 * @see GitHubStrikethroughNode
 * @see GitHubStrikethroughRendererExtension
 */
public class GitHubStrikethroughProcessorExtension(requireTwoTildes: Boolean = false) : MarkdownProcessorExtension {
    private val commonMarkExtension = StrikethroughExtension.builder().requireTwoTildes(requireTwoTildes).build()

    override val parserExtension: ParserExtension = commonMarkExtension as ParserExtension

    override val textRendererExtension: TextContentRenderer.TextContentRendererExtension =
        commonMarkExtension as TextContentRenderer.TextContentRendererExtension

    override val delimitedInlineProcessorExtension: MarkdownDelimitedInlineProcessorExtension =
        GitHubStrikethroughInlineProcessorExtension
}
