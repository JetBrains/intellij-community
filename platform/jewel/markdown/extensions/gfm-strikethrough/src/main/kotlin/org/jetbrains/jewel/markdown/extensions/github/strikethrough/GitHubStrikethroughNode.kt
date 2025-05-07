package org.jetbrains.jewel.markdown.extensions.github.strikethrough

import org.jetbrains.jewel.markdown.InlineMarkdown
import org.jetbrains.jewel.markdown.WithInlineMarkdown

/**
 * A Markdown inline node representing a strike-through. It contains other inline nodes.
 *
 * Strikethrough is a GitHub Flavored Markdown extension, defined
 * [in the GFM specs](https://github.github.com/gfm/#strikethrough-extension-).
 *
 * @see org.commonmark.ext.gfm.strikethrough.Strikethrough
 * @see GitHubStrikethroughProcessorExtension
 * @see GitHubStrikethroughRendererExtension
 */
public data class GitHubStrikethroughNode(val delimiter: String, override val inlineContent: List<InlineMarkdown>) :
    InlineMarkdown.CustomDelimitedNode, WithInlineMarkdown {
    override val openingDelimiter: String = delimiter
}
