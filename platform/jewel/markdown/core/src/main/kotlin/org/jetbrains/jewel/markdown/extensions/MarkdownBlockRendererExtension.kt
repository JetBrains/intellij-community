package org.jetbrains.jewel.markdown.extensions

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.jewel.foundation.ExperimentalJewelApi
import org.jetbrains.jewel.markdown.MarkdownBlock
import org.jetbrains.jewel.markdown.MarkdownBlock.CustomBlock
import org.jetbrains.jewel.markdown.rendering.InlineMarkdownRenderer
import org.jetbrains.jewel.markdown.rendering.MarkdownBlockRenderer

/** An extension for [MarkdownBlockRenderer] that can render one or more [MarkdownBlock.CustomBlock]s. */
@ApiStatus.Experimental
@ExperimentalJewelApi
public interface MarkdownBlockRendererExtension {
    /**
     * Check whether the provided [block] can be rendered by this extension.
     *
     * @param block The block to check for renderability.
     * @return `true` if this extension can render the given [block], `false` otherwise.
     */
    public fun canRender(block: CustomBlock): Boolean

    /**
     * Render a [MarkdownBlock.CustomBlock] as a native Composable. Note that if [canRender] returns `false` for
     * [block], the implementation might throw.
     *
     * @param block The [MarkdownBlock.CustomBlock] to render.
     * @param blockRenderer The [MarkdownBlockRenderer] to use to render other blocks if necessary.
     * @param inlineRenderer The [InlineMarkdownRenderer] to use to render inline elements if necessary.
     * @param enabled Whether The rendered content should be enabled.
     * @param modifier The modifier to be applied to the composable.
     * @param onUrlClick The callback to invoke when an URL is clicked.
     * @param onTextClick The callback to invoke when a text part is clicked.
     */
    @Composable
    public fun render(
        block: CustomBlock,
        blockRenderer: MarkdownBlockRenderer,
        inlineRenderer: InlineMarkdownRenderer,
        enabled: Boolean,
        modifier: Modifier,
        onUrlClick: (String) -> Unit,
        onTextClick: () -> Unit,
    )
}
