package org.jetbrains.jewel.markdown.extensions

import org.commonmark.node.CustomBlock
import org.jetbrains.jewel.markdown.MarkdownBlock
import org.jetbrains.jewel.markdown.processing.MarkdownProcessor

public interface MarkdownBlockProcessorExtension {
    /**
     * Returns true if the [block] can be processed by this extension instance.
     *
     * @param block The [CustomBlock] to parse
     */
    public fun canProcess(block: CustomBlock): Boolean

    /**
     * Processes the [block] as a [MarkdownBlock.CustomBlock], if possible. Note that you should always check that
     * [canProcess] returns true for the same [block], as implementations might throw an exception for unsupported block
     * types.
     */
    public fun processMarkdownBlock(block: CustomBlock, processor: MarkdownProcessor): MarkdownBlock.CustomBlock?
}
