package org.jetbrains.jewel.markdown.rendering

import androidx.compose.runtime.Composable
import org.jetbrains.jewel.foundation.ExperimentalJewelApi
import org.jetbrains.jewel.markdown.MarkdownBlock
import org.jetbrains.jewel.markdown.MarkdownBlock.BlockQuote
import org.jetbrains.jewel.markdown.MarkdownBlock.CodeBlock
import org.jetbrains.jewel.markdown.MarkdownBlock.CodeBlock.FencedCodeBlock
import org.jetbrains.jewel.markdown.MarkdownBlock.CodeBlock.IndentedCodeBlock
import org.jetbrains.jewel.markdown.MarkdownBlock.Heading.H1
import org.jetbrains.jewel.markdown.MarkdownBlock.Heading.H2
import org.jetbrains.jewel.markdown.MarkdownBlock.Heading.H3
import org.jetbrains.jewel.markdown.MarkdownBlock.Heading.H4
import org.jetbrains.jewel.markdown.MarkdownBlock.Heading.H5
import org.jetbrains.jewel.markdown.MarkdownBlock.Heading.H6
import org.jetbrains.jewel.markdown.MarkdownBlock.HtmlBlock
import org.jetbrains.jewel.markdown.MarkdownBlock.Image
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
    public fun render(block: H1, styling: MarkdownStyling.Heading.H1)

    @Composable
    public fun render(block: H2, styling: MarkdownStyling.Heading.H2)

    @Composable
    public fun render(block: H3, styling: MarkdownStyling.Heading.H3)

    @Composable
    public fun render(block: H4, styling: MarkdownStyling.Heading.H4)

    @Composable
    public fun render(block: H5, styling: MarkdownStyling.Heading.H5)

    @Composable
    public fun render(block: H6, styling: MarkdownStyling.Heading.H6)

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
    public fun render(block: Image, styling: MarkdownStyling.Image)

    @Composable
    public fun renderThematicBreak(styling: MarkdownStyling.ThematicBreak)

    @Composable
    public fun render(block: HtmlBlock, styling: MarkdownStyling.HtmlBlock)

    public companion object
}
