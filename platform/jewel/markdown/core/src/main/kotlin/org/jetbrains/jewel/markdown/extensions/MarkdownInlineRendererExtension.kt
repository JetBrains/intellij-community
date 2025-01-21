package org.jetbrains.jewel.markdown.extensions

import org.jetbrains.jewel.markdown.InlineMarkdown
import org.jetbrains.jewel.markdown.InlineMarkdown.CustomNode
import org.jetbrains.jewel.markdown.rendering.InlineMarkdownRenderer

/** An extension for [InlineMarkdownRenderer] that can render one or more [InlineMarkdown.CustomNode]s. */
public interface MarkdownInlineRendererExtension {
    /** Check whether the provided [inline] can be rendered by this extension. */
    public fun canRender(inline: CustomNode): Boolean

    /**
     * Render a [CustomNode] as an annotated string. Note that if [canRender] returns `false` for [inline], the
     * implementation might throw.
     */
    public fun render(inline: CustomNode, inlineRenderer: InlineMarkdownRenderer, enabled: Boolean)
}
