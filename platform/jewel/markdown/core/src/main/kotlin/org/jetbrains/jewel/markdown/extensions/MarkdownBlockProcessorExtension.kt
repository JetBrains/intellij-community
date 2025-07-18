package org.jetbrains.jewel.markdown.extensions

import org.commonmark.node.CustomBlock
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.jewel.foundation.ExperimentalJewelApi
import org.jetbrains.jewel.markdown.MarkdownBlock
import org.jetbrains.jewel.markdown.processing.MarkdownProcessor

/** Extension that can process a custom block-level [CustomBlock]. */
@ApiStatus.Experimental
@ExperimentalJewelApi
public interface MarkdownBlockProcessorExtension {
    /**
     * Returns true if the [block] can be processed by this extension instance.
     *
     * @param block The [CustomBlock] to parse
     * @return True if this extension can process the provided [CustomBlock], false otherwise.
     */
    public fun canProcess(block: CustomBlock): Boolean

    /**
     * Processes the [block] as a [MarkdownBlock.CustomBlock], if possible. Note that you should always check that
     * [canProcess] returns true for the same [block], as implementations might throw an exception for unsupported block
     * types.
     *
     * @param block The [CustomBlock] to process, for which this extension's [canProcess] returned true.
     * @param processor The [MarkdownProcessor] to use for processing.
     * @return null if the processing fails, otherwise the processed [MarkdownBlock.CustomBlock].
     */
    public fun processMarkdownBlock(block: CustomBlock, processor: MarkdownProcessor): MarkdownBlock.CustomBlock?
}
