package org.jetbrains.jewel.markdown.extensions

import androidx.compose.ui.text.AnnotatedString
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.jewel.foundation.ExperimentalJewelApi
import org.jetbrains.jewel.markdown.InlineMarkdown
import org.jetbrains.jewel.markdown.InlineMarkdown.CustomDelimitedNode
import org.jetbrains.jewel.markdown.rendering.InlineMarkdownRenderer
import org.jetbrains.jewel.markdown.rendering.InlinesStyling

/**
 * An extension for [InlineMarkdownRenderer] that can render [InlineMarkdown.CustomDelimitedNode]s backed by a
 * [org.commonmark.node.Delimited] node.
 *
 * Only `Delimited` nodes that can be rendered as an [AnnotatedString] are supported; other kinds of inline node aren't.
 */
@ApiStatus.Experimental
@ExperimentalJewelApi
public interface MarkdownDelimitedInlineRendererExtension {
    /**
     * Check whether the provided [node] node can be rendered by this extension.
     *
     * @param node The [CustomDelimitedNode] to check.
     * @return True if this instance can render [node], false otherwise.
     */
    public fun canRender(node: CustomDelimitedNode): Boolean

    /**
     * Render a [CustomDelimitedNode] into an [AnnotatedString.Builder]. Note that if [canRender] returns `false` for
     * [node], the implementation might throw.
     *
     * @param node The [CustomDelimitedNode] to render.
     * @param inlineRenderer The [InlineMarkdownRenderer] to use to render the node and its content.
     * @param inlinesStyling The styling to use to render the node and its content.
     * @param enabled When false, the node will be rendered with a disabled appearance (e.g., grayed out).
     * @param onUrlClicked Lambda that will be invoked when URLs inside this node are clicked.
     */
    public fun render(
        node: CustomDelimitedNode,
        inlineRenderer: InlineMarkdownRenderer,
        inlinesStyling: InlinesStyling,
        enabled: Boolean,
        onUrlClicked: ((String) -> Unit)?,
    ): AnnotatedString
}
