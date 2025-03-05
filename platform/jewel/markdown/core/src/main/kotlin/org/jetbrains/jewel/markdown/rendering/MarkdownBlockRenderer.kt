package org.jetbrains.jewel.markdown.rendering

import androidx.compose.runtime.Composable
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
@ExperimentalJewelApi
public interface MarkdownBlockRenderer {
    public val rootStyling: MarkdownStyling
    public val rendererExtensions: List<MarkdownRendererExtension>
    public val inlineRenderer: InlineMarkdownRenderer

    @Composable
    public fun render(
        blocks: List<MarkdownBlock>,
        enabled: Boolean,
        onUrlClick: (String) -> Unit,
        onTextClick: () -> Unit,
    )

    @Composable
    public fun render(block: MarkdownBlock, enabled: Boolean, onUrlClick: (String) -> Unit, onTextClick: () -> Unit)

    @Composable
    public fun render(
        block: Paragraph,
        styling: MarkdownStyling.Paragraph,
        enabled: Boolean,
        onUrlClick: (String) -> Unit,
        onTextClick: () -> Unit,
    )

    @Composable
    public fun render(
        block: MarkdownBlock.Heading,
        styling: MarkdownStyling.Heading,
        enabled: Boolean,
        onUrlClick: (String) -> Unit,
        onTextClick: () -> Unit,
    )

    @Composable
    public fun render(
        block: MarkdownBlock.Heading,
        styling: MarkdownStyling.Heading.HN,
        enabled: Boolean,
        onUrlClick: (String) -> Unit,
        onTextClick: () -> Unit,
    )

    @Composable
    public fun render(
        block: BlockQuote,
        styling: MarkdownStyling.BlockQuote,
        enabled: Boolean,
        onUrlClick: (String) -> Unit,
        onTextClick: () -> Unit,
    )

    @Composable
    public fun render(
        block: ListBlock,
        styling: MarkdownStyling.List,
        enabled: Boolean,
        onUrlClick: (String) -> Unit,
        onTextClick: () -> Unit,
    )

    @Composable
    public fun render(
        block: OrderedList,
        styling: MarkdownStyling.List.Ordered,
        enabled: Boolean,
        onUrlClick: (String) -> Unit,
        onTextClick: () -> Unit,
    )

    @Composable
    public fun render(
        block: UnorderedList,
        styling: MarkdownStyling.List.Unordered,
        enabled: Boolean,
        onUrlClick: (String) -> Unit,
        onTextClick: () -> Unit,
    )

    @Composable
    public fun render(block: ListItem, enabled: Boolean, onUrlClick: (String) -> Unit, onTextClick: () -> Unit)

    @Composable public fun render(block: CodeBlock, styling: MarkdownStyling.Code)

    @Composable public fun render(block: IndentedCodeBlock, styling: MarkdownStyling.Code.Indented)

    @Composable public fun render(block: FencedCodeBlock, styling: MarkdownStyling.Code.Fenced)

    @Composable public fun renderThematicBreak(styling: MarkdownStyling.ThematicBreak)

    @Composable public fun render(block: HtmlBlock, styling: MarkdownStyling.HtmlBlock)

    /**
     * Creates a copy of this instance, using the provided non-null parameters, or the current values for the null ones.
     */
    public fun createCopy(
        rootStyling: MarkdownStyling? = null,
        rendererExtensions: List<MarkdownRendererExtension>? = null,
        inlineRenderer: InlineMarkdownRenderer? = null,
    ): MarkdownBlockRenderer

    /** Creates a copy of this [MarkdownBlockRenderer] with the same properties, plus the provided [extension]. */
    @ExperimentalJewelApi public operator fun plus(extension: MarkdownRendererExtension): MarkdownBlockRenderer

    public companion object
}
