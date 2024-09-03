package org.jetbrains.jewel.markdown.extensions

import androidx.compose.runtime.Composable
import org.jetbrains.jewel.markdown.MarkdownBlock
import org.jetbrains.jewel.markdown.MarkdownBlock.CustomBlock
import org.jetbrains.jewel.markdown.rendering.InlineMarkdownRenderer
import org.jetbrains.jewel.markdown.rendering.MarkdownBlockRenderer

/** An extension for [MarkdownBlockRenderer] that can render one or more [MarkdownBlock.CustomBlock]s. */
public interface MarkdownBlockRendererExtension {
    /** Check whether the provided [block] can be rendered by this extension. */
    public fun canRender(block: CustomBlock): Boolean

    /**
     * Render a [MarkdownBlock.CustomBlock] as a native Composable. Note that if [canRender] returns `false` for
     * [block], the implementation might throw.
     */
    @Composable
    public fun render(
        block: CustomBlock,
        blockRenderer: MarkdownBlockRenderer,
        inlineRenderer: InlineMarkdownRenderer,
        enabled: Boolean,
        onUrlClick: (String) -> Unit,
        onTextClick: () -> Unit,
    )
}
