package org.jetbrains.jewel.markdown.rendering

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.jewel.foundation.ExperimentalJewelApi
import org.jetbrains.jewel.markdown.MarkdownBlock
import org.jetbrains.jewel.markdown.MarkdownBlock.BlockQuote
import org.jetbrains.jewel.markdown.MarkdownBlock.CodeBlock
import org.jetbrains.jewel.markdown.MarkdownBlock.CodeBlock.FencedCodeBlock
import org.jetbrains.jewel.markdown.MarkdownBlock.CodeBlock.IndentedCodeBlock
import org.jetbrains.jewel.markdown.MarkdownBlock.HtmlBlock
import org.jetbrains.jewel.markdown.MarkdownBlock.ListBlock
import org.jetbrains.jewel.markdown.MarkdownBlock.ListBlock.OrderedList
import org.jetbrains.jewel.markdown.MarkdownBlock.ListBlock.UnorderedList
import org.jetbrains.jewel.markdown.MarkdownBlock.ListItem
import org.jetbrains.jewel.markdown.MarkdownBlock.Paragraph
import org.jetbrains.jewel.markdown.extensions.MarkdownRendererExtension

/**
 * Renders one or more [MarkdownBlock]s into a Compose UI.
 *
 * @param rootStyling The [MarkdownStyling] to use to render the Markdown into composables.
 * @param rendererExtensions The [MarkdownRendererExtension]s used to render [MarkdownBlock.CustomBlock]s.
 * @param inlineRenderer The [InlineMarkdownRenderer] used to render
 *   [inline content][org.jetbrains.jewel.markdown.InlineMarkdown].
 * @see render
 */
@Suppress("ComposableNaming")
@ApiStatus.Experimental
@ExperimentalJewelApi
public interface MarkdownBlockRenderer {
    public val rootStyling: MarkdownStyling
    public val rendererExtensions: List<MarkdownRendererExtension>
    public val inlineRenderer: InlineMarkdownRenderer

    /**
     * Renders a list of [MarkdownBlock]s into a Compose UI.
     *
     * @param blocks The list of blocks to render.
     * @param enabled True if the blocks should be enabled, false otherwise.
     * @param onUrlClick The callback invoked when the user clicks on a URL.
     * @param onTextClick The callback invoked when the user clicks on a text.
     * @param modifier The modifier to be applied to the root composable (usually, a `Column` or `LazyColumn`).
     * @see render
     */
    @Composable
    public fun render(
        blocks: List<MarkdownBlock>,
        enabled: Boolean,
        onUrlClick: (String) -> Unit,
        onTextClick: () -> Unit,
        modifier: Modifier,
    )

    /**
     * Renders a [MarkdownBlock] into a Compose UI.
     *
     * @param block The block to render.
     * @param enabled True if the block should be enabled, false otherwise.
     * @param onUrlClick The callback invoked when the user clicks on a URL.
     * @param onTextClick The callback invoked when the user clicks on a text.
     * @param modifier The modifier to be applied to the composable.
     * @see render
     */
    @Composable
    public fun render(
        block: MarkdownBlock,
        enabled: Boolean,
        onUrlClick: (String) -> Unit,
        onTextClick: () -> Unit,
        modifier: Modifier,
    )

    /**
     * Renders a [Paragraph] into a Compose UI.
     *
     * @param block The paragraph to render.
     * @param styling The [`Paragraph`][MarkdownStyling.Paragraph] styling to use to render.
     * @param enabled True if the block should be enabled, false otherwise.
     * @param onUrlClick The callback invoked when the user clicks on a URL.
     * @param onTextClick The callback invoked when the user clicks on a text.
     * @param modifier The modifier to be applied to the composable.
     * @see render
     */
    @Composable
    public fun render(
        block: Paragraph,
        styling: MarkdownStyling.Paragraph,
        enabled: Boolean,
        onUrlClick: (String) -> Unit,
        onTextClick: () -> Unit,
        modifier: Modifier,
    )

    /**
     * Renders a [MarkdownBlock.Heading] into a Compose UI.
     *
     * @param block The heading to render.
     * @param styling The [`Heading`][MarkdownStyling.Heading] styling to use to render.
     * @param enabled True if the block should be enabled, false otherwise.
     * @param onUrlClick The callback invoked when the user clicks on a URL.
     * @param onTextClick The callback invoked when the user clicks on a text.
     * @param modifier The modifier to be applied to the composable.
     * @see render
     */
    @Composable
    public fun render(
        block: MarkdownBlock.Heading,
        styling: MarkdownStyling.Heading,
        enabled: Boolean,
        onUrlClick: (String) -> Unit,
        onTextClick: () -> Unit,
        modifier: Modifier,
    )

    /**
     * Renders a [MarkdownBlock.Heading] into a Compose UI, using a specific [`HN`][MarkdownStyling.Heading.HN] styling.
     *
     * @param block The heading to render.
     * @param styling The [`Heading.HN`][MarkdownStyling.Heading.HN] styling to use to render.
     * @param enabled True if the block should be enabled, false otherwise.
     * @param onUrlClick The callback invoked when the user clicks on a URL.
     * @param onTextClick The callback invoked when the user clicks on a text.
     * @param modifier The modifier to be applied to the composable.
     * @see render
     */
    @Composable
    public fun render(
        block: MarkdownBlock.Heading,
        styling: MarkdownStyling.Heading.HN,
        enabled: Boolean,
        onUrlClick: (String) -> Unit,
        onTextClick: () -> Unit,
        modifier: Modifier,
    )

    /**
     * Renders a [BlockQuote] into a Compose UI.
     *
     * @param block The blockquote to render.
     * @param styling The [`BlockQuote`][MarkdownStyling.BlockQuote] styling to use to render.
     * @param enabled True if the block should be enabled, false otherwise.
     * @param onUrlClick The callback invoked when the user clicks on a URL.
     * @param onTextClick The callback invoked when the user clicks on a text.
     * @param modifier The modifier to be applied to the composable.
     * @see render
     */
    @Composable
    public fun render(
        block: BlockQuote,
        styling: MarkdownStyling.BlockQuote,
        enabled: Boolean,
        onUrlClick: (String) -> Unit,
        onTextClick: () -> Unit,
        modifier: Modifier,
    )

    /**
     * Renders a [ListBlock] into a Compose UI.
     *
     * @param block The list to render.
     * @param styling The [`List`][MarkdownStyling.List] styling to use to render.
     * @param enabled True if the block should be enabled, false otherwise.
     * @param onUrlClick The callback invoked when the user clicks on a URL.
     * @param onTextClick The callback invoked when the user clicks on a text.
     * @param modifier The modifier to be applied to the composable.
     * @see render
     */
    @Composable
    public fun render(
        block: ListBlock,
        styling: MarkdownStyling.List,
        enabled: Boolean,
        onUrlClick: (String) -> Unit,
        onTextClick: () -> Unit,
        modifier: Modifier,
    )

    /**
     * Renders a [OrderedList] into a Compose UI.
     *
     * @param block The ordered list to render.
     * @param styling The [`List.Ordered`][MarkdownStyling.List.Ordered] styling to use to render.
     * @param enabled True if the block should be enabled, false otherwise.
     * @param onUrlClick The callback invoked when the user clicks on a URL.
     * @param onTextClick The callback invoked when the user clicks on a text.
     * @param modifier The modifier to be applied to the composable.
     * @see render
     */
    @Composable
    public fun render(
        block: OrderedList,
        styling: MarkdownStyling.List.Ordered,
        enabled: Boolean,
        onUrlClick: (String) -> Unit,
        onTextClick: () -> Unit,
        modifier: Modifier,
    )

    /**
     * Renders a [UnorderedList] into a Compose UI.
     *
     * @param block The unordered list to render.
     * @param styling The [`List.Unordered`][MarkdownStyling.List.Unordered] styling to use to render.
     * @param enabled True if the block should be enabled, false otherwise.
     * @param onUrlClick The callback invoked when the user clicks on a URL.
     * @param onTextClick The callback invoked when the user clicks on a text.
     * @param modifier The modifier to be applied to the composable.
     * @see render
     */
    @Composable
    public fun render(
        block: UnorderedList,
        styling: MarkdownStyling.List.Unordered,
        enabled: Boolean,
        onUrlClick: (String) -> Unit,
        onTextClick: () -> Unit,
        modifier: Modifier,
    )

    /**
     * Renders a [ListItem] into a Compose UI.
     *
     * @param block The list item to render.
     * @param enabled True if the block should be enabled, false otherwise.
     * @param onUrlClick The callback invoked when the user clicks on a URL.
     * @param onTextClick The callback invoked when the user clicks on a text.
     * @param modifier The modifier to be applied to the composable.
     * @see render
     */
    @Composable
    public fun render(
        block: ListItem,
        enabled: Boolean,
        onUrlClick: (String) -> Unit,
        onTextClick: () -> Unit,
        modifier: Modifier,
    )

    /**
     * Renders a [CodeBlock] into a Compose UI.
     *
     * @param block The heading to render.
     * @param styling The [`Code`][MarkdownStyling.Code] styling to use to render.
     * @param enabled True if the block should be enabled, false otherwise.
     * @param modifier The modifier to be applied to the composable.
     * @see render
     */
    @Composable public fun render(block: CodeBlock, styling: MarkdownStyling.Code, enabled: Boolean, modifier: Modifier)

    /**
     * Renders a [IndentedCodeBlock] into a Compose UI.
     *
     * @param block The heading to render.
     * @param styling The [`Code.Indented`][MarkdownStyling.Code.Indented] styling to use to render.
     * @param enabled True if the block should be enabled, false otherwise.
     * @param modifier The modifier to be applied to the composable.
     * @see render
     */
    @Composable
    public fun render(
        block: IndentedCodeBlock,
        styling: MarkdownStyling.Code.Indented,
        enabled: Boolean,
        modifier: Modifier,
    )

    /**
     * Renders a [FencedCodeBlock] into a Compose UI. If the fenced block defines a language, it can be
     * syntax-highlighted by the the [org.jetbrains.jewel.foundation.code.highlighting.LocalCodeHighlighter].
     *
     * @param block The heading to render.
     * @param styling The [`Code.Fenced`][MarkdownStyling.Code.Fenced] styling to use to render.
     * @param enabled True if the block should be enabled, false otherwise.
     * @param modifier The modifier to be applied to the composable.
     * @see render
     * @see org.jetbrains.jewel.foundation.code.highlighting.CodeHighlighter
     */
    @Composable
    public fun render(
        block: FencedCodeBlock,
        styling: MarkdownStyling.Code.Fenced,
        enabled: Boolean,
        modifier: Modifier,
    )

    /**
     * Renders a thematic break (horizontal divider) into a Compose UI.
     *
     * @param styling The [`ThematicBreak`][MarkdownStyling.ThematicBreak] styling to use to render.
     * @param enabled True if the block should be enabled, false otherwise.
     * @param modifier The modifier to be applied to the composable.
     * @see render
     */
    @Composable
    public fun renderThematicBreak(styling: MarkdownStyling.ThematicBreak, enabled: Boolean, modifier: Modifier)

    /**
     * Renders a [HtmlBlock] into a Compose UI. Since Compose can't render HTML out of the box, this might result in a
     * no-op (e.g., in [DefaultMarkdownBlockRenderer.render]).
     *
     * @param block The heading to render.
     * @param styling The [`Code`][MarkdownStyling.Code] styling to use to render.
     * @param enabled True if the block should be enabled, false otherwise.
     * @param modifier The modifier to be applied to the composable.
     * @see render
     */
    @Composable
    public fun render(block: HtmlBlock, styling: MarkdownStyling.HtmlBlock, enabled: Boolean, modifier: Modifier)

    /**
     * Creates a copy of this instance, using the provided non-null parameters, or the current values for the null ones.
     *
     * @param rootStyling The [MarkdownStyling] to use to render the Markdown into composables.
     * @param rendererExtensions The [MarkdownRendererExtension]s used to render [MarkdownBlock.CustomBlock]s.
     * @param inlineRenderer The [InlineMarkdownRenderer] used to render inline content.
     */
    public fun createCopy(
        rootStyling: MarkdownStyling? = null,
        rendererExtensions: List<MarkdownRendererExtension>? = null,
        inlineRenderer: InlineMarkdownRenderer? = null,
    ): MarkdownBlockRenderer

    /**
     * Creates a copy of this [MarkdownBlockRenderer] with the same properties, plus the provided [extension].
     *
     * @see createCopy
     */
    @ApiStatus.Experimental
    @ExperimentalJewelApi
    public operator fun plus(extension: MarkdownRendererExtension): MarkdownBlockRenderer

    /** The companion object for [MarkdownBlockRenderer]. */
    public companion object
}
