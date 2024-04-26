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

@ExperimentalJewelApi
public interface MarkdownBlockRenderer {

    @Composable
    public fun render(
        blocks: List<MarkdownBlock>,
        onUrlClick: (String) -> Unit,
        onTextClick: () -> Unit,
    )

    @Composable
    public fun render(
        block: MarkdownBlock,
        onUrlClick: (String) -> Unit,
        onTextClick: () -> Unit,
    )

    @Composable
    public fun render(
        block: Paragraph,
        styling: MarkdownStyling.Paragraph,
        onUrlClick: (String) -> Unit,
        onTextClick: () -> Unit,
    )

    @Composable
    public fun render(
        block: MarkdownBlock.Heading,
        styling: MarkdownStyling.Heading,
        onUrlClick: (String) -> Unit,
        onTextClick: () -> Unit,
    )

    @Composable
    public fun render(
        block: MarkdownBlock.Heading,
        styling: MarkdownStyling.Heading.HN,
        onUrlClick: (String) -> Unit,
        onTextClick: () -> Unit,
    )

    @Composable
    public fun render(
        block: BlockQuote,
        styling: MarkdownStyling.BlockQuote,
        onUrlClick: (String) -> Unit,
        onTextClick: () -> Unit,
    )

    @Composable
    public fun render(
        block: ListBlock,
        styling: MarkdownStyling.List,
        onUrlClick: (String) -> Unit,
        onTextClick: () -> Unit,
    )

    @Composable
    public fun render(
        block: OrderedList,
        styling: MarkdownStyling.List.Ordered,
        onUrlClick: (String) -> Unit,
        onTextClick: () -> Unit,
    )

    @Composable
    public fun render(
        block: UnorderedList,
        styling: MarkdownStyling.List.Unordered,
        onUrlClick: (String) -> Unit,
        onTextClick: () -> Unit,
    )

    @Composable
    public fun render(
        block: ListItem,
        onUrlClick: (String) -> Unit,
        onTextClick: () -> Unit,
    )

    @Composable
    public fun render(block: CodeBlock, styling: MarkdownStyling.Code)

    @Composable
    public fun render(block: IndentedCodeBlock, styling: MarkdownStyling.Code.Indented)

    @Composable
    public fun render(block: FencedCodeBlock, styling: MarkdownStyling.Code.Fenced)

    @Composable
    public fun renderThematicBreak(styling: MarkdownStyling.ThematicBreak)

    @Composable
    public fun render(block: HtmlBlock, styling: MarkdownStyling.HtmlBlock)

    public companion object
}
