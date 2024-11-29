package org.jetbrains.jewel.markdown.extensions

import org.commonmark.node.CustomNode
import org.jetbrains.jewel.markdown.InlineMarkdown
import org.jetbrains.jewel.markdown.processing.MarkdownProcessor

public interface MarkdownInlineProcessorExtension {
    /**
     * Returns true if the [node] can be processed by this extension instance.
     *
     * @param node The [CustomNode] to parse
     */
    public fun canProcess(node: CustomNode): Boolean

    /**
     * Processes the [node] as a [InlineMarkdown.CustomNode], if possible. Note that you should always check that
     * [canProcess] returns true for the same [node], as implementations might throw an exception for unsupported node
     * types.
     */
    public fun processInlineMarkdown(node: CustomNode, processor: MarkdownProcessor): InlineMarkdown.CustomNode?
}
