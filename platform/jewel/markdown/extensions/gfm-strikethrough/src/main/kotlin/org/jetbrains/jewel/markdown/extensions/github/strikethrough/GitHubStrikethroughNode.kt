package org.jetbrains.jewel.markdown.extensions.github.strikethrough

import org.jetbrains.annotations.ApiStatus
import org.jetbrains.jewel.foundation.ExperimentalJewelApi
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
@ApiStatus.Experimental
@ExperimentalJewelApi
public data class GitHubStrikethroughNode(
    /** The delimiter string used to mark the start and end of the strikethrough (e.g., `~~`). */
    val delimiter: String,
    /** The inline child nodes contained within this strikethrough span. */
    override val inlineContent: List<InlineMarkdown>,
) : InlineMarkdown.CustomDelimitedNode, WithInlineMarkdown {
    /** The opening delimiter string, equal to [delimiter]. */
    override val openingDelimiter: String = delimiter
}
