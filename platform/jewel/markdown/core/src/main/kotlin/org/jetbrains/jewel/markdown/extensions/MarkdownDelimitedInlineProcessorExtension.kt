package org.jetbrains.jewel.markdown.extensions

import org.commonmark.node.Delimited
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.jewel.foundation.ExperimentalJewelApi
import org.jetbrains.jewel.markdown.InlineMarkdown
import org.jetbrains.jewel.markdown.processing.MarkdownProcessor

/** An extension for parsing [org.commonmark.node.Delimited] inline nodes. */
@ApiStatus.Experimental
@ExperimentalJewelApi
public interface MarkdownDelimitedInlineProcessorExtension {
    /** Checks whether the [delimited] can be processed by this instance. */
    public fun canProcess(delimited: Delimited): Boolean

    /**
     * Processes a [delimited] for which [canProcess] returned `true` into a [InlineMarkdown.CustomDelimitedNode].
     *
     * @param delimited A [Delimited] produced by a [MarkdownProcessor].
     * @param markdownProcessor The [MarkdownProcessor] to use to parse this [Delimited].
     * @return The [InlineMarkdown.CustomDelimitedNode] obtained from [delimited].
     */
    public fun processDelimitedInline(
        delimited: Delimited,
        markdownProcessor: MarkdownProcessor,
    ): InlineMarkdown.CustomDelimitedNode
}
