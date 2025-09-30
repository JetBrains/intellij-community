package org.jetbrains.jewel.markdown.extensions

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.jewel.foundation.ExperimentalJewelApi
import org.jetbrains.jewel.markdown.MarkdownBlock
import org.jetbrains.jewel.markdown.MarkdownBlock.CustomBlock
import org.jetbrains.jewel.markdown.rendering.InlineMarkdownRenderer
import org.jetbrains.jewel.markdown.rendering.MarkdownBlockRenderer

/**
 * An extension for [MarkdownBlockRenderer] that can render one or more [MarkdownBlock.CustomBlock]s.
 *
 * This is the primary way to handle custom Markdown elements that are not part of the standard Markdown syntax.
 * Implement this interface to define how a specific type of [CustomBlock] should be rendered.
 *
 * If you want to customize how a standard block is rendered, you can create a new [MarkdownBlockRenderer] with the
 * required custom behavior. Extending [org.jetbrains.jewel.markdown.rendering.DefaultMarkdownBlockRenderer] is the
 * simplest choice in that case.
 *
 * Implementations are responsible for both checking if they can render a given block, through the [canRender] method,
 * and for providing the Composable content for it, through the [RenderCustomBlock] method.
 */
@ApiStatus.Experimental
@ExperimentalJewelApi
public interface MarkdownBlockRendererExtension {
    /**
     * Check whether the provided [block] can be rendered by this extension.
     *
     * This method is called by the [MarkdownBlockRenderer] to determine which extension should handle a given
     * [CustomBlock].
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
     * @param onUrlClick The callback to invoke when a URL is clicked.
     * @param onTextClick The callback to invoke when a text part is clicked. **Ignored.**
     */
    @Deprecated(
        "Use RenderCustomBlock instead; the onTextClick parameter is no longer supported.",
        ReplaceWith("RenderCustomBlock(block, blockRenderer, inlineRenderer, enabled, modifier, onUrlClick)"),
    )
    @Suppress("ComposableNaming")
    @Composable
    public fun render(
        block: CustomBlock,
        blockRenderer: MarkdownBlockRenderer,
        inlineRenderer: InlineMarkdownRenderer,
        enabled: Boolean,
        modifier: Modifier,
        onUrlClick: (String) -> Unit,
        onTextClick: () -> Unit,
    ) {
        RenderCustomBlock(block, blockRenderer, inlineRenderer, enabled, modifier, onUrlClick)
    }

    /**
     * Renders a [MarkdownBlock.CustomBlock] as a native Composable.
     *
     * Note that if [canRender] returns `false` for a given [block], the implementation of this method may throw an
     * exception.
     *
     * @param block The [MarkdownBlock.CustomBlock] to render.
     * @param blockRenderer The [MarkdownBlockRenderer] to use to render other blocks if necessary.
     * @param inlineRenderer The [InlineMarkdownRenderer] to use to render inline elements if necessary.
     * @param enabled Whether the rendered content should be interactive. When `false`, components like links will not
     *   be clickable.
     * @param modifier The modifier to be applied to the composable.
     * @param onUrlClick The callback to invoke when a URL is clicked.
     */
    @Composable
    public fun RenderCustomBlock(
        block: CustomBlock,
        blockRenderer: MarkdownBlockRenderer,
        inlineRenderer: InlineMarkdownRenderer,
        enabled: Boolean,
        modifier: Modifier,
        onUrlClick: (String) -> Unit,
    )
}
