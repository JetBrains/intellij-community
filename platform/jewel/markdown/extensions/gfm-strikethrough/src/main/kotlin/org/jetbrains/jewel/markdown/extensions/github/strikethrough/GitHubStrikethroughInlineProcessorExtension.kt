package org.jetbrains.jewel.markdown.extensions.github.strikethrough

import org.commonmark.ext.gfm.strikethrough.Strikethrough
import org.commonmark.node.Delimited
import org.commonmark.node.Node
import org.jetbrains.jewel.markdown.InlineMarkdown
import org.jetbrains.jewel.markdown.extensions.MarkdownDelimitedInlineProcessorExtension
import org.jetbrains.jewel.markdown.processing.MarkdownProcessor
import org.jetbrains.jewel.markdown.processing.readInlineMarkdown

/**
 * A [MarkdownDelimitedInlineProcessorExtension] that turns [Strikethrough] nodes into [GitHubStrikethroughNode]s.
 *
 * Strikethrough is a GitHub Flavored Markdown extension, defined
 * [in the GFM specs](https://github.github.com/gfm/#strikethrough-extension-).
 *
 * @see GitHubStrikethroughProcessorExtension
 * @see GitHubStrikethroughNode
 * @see GitHubStrikethroughRendererExtension
 */
public object GitHubStrikethroughInlineProcessorExtension : MarkdownDelimitedInlineProcessorExtension {
    override fun canProcess(delimited: Delimited): Boolean = delimited is Strikethrough

    override fun processDelimitedInline(
        delimited: Delimited,
        markdownProcessor: MarkdownProcessor,
    ): InlineMarkdown.CustomDelimitedNode =
        GitHubStrikethroughNode(
            delimited.openingDelimiter,
            // Alas, CommonMark APIs kinda suck, so we need to hard-cast to Node...
            (delimited as Node).readInlineMarkdown(markdownProcessor),
        )
}
