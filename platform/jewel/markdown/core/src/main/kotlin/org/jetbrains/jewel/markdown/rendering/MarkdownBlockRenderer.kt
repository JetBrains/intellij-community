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

@ExperimentalJewelApi
public interface MarkdownBlockRenderer {

    @Composable
    public fun render(blocks: List<MarkdownBlock>)

    @Composable
    public fun render(block: MarkdownBlock)

    @Composable
    public fun render(block: MarkdownBlock.Paragraph, styling: MarkdownStyling.Paragraph)

    @Composable
    public fun render(block: MarkdownBlock.Heading, styling: MarkdownStyling.Heading)

    @Composable
    public fun render(block: MarkdownBlock.Heading, styling: MarkdownStyling.Heading.HN)

    @Composable
    public fun render(block: BlockQuote, styling: MarkdownStyling.BlockQuote)

    @Composable
    public fun render(block: ListBlock, styling: MarkdownStyling.List)

    @Composable
    public fun render(block: OrderedList, styling: MarkdownStyling.List.Ordered)

    @Composable
    public fun render(block: UnorderedList, styling: MarkdownStyling.List.Unordered)

    @Composable
    public fun render(block: ListItem)

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
