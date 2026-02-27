package org.jetbrains.jewel.markdown.extensions.frontmatter

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.jewel.foundation.ExperimentalJewelApi
import org.jetbrains.jewel.foundation.layout.BasicTableLayout
import org.jetbrains.jewel.markdown.InlineMarkdown
import org.jetbrains.jewel.markdown.MarkdownBlock
import org.jetbrains.jewel.markdown.MarkdownBlock.CustomBlock
import org.jetbrains.jewel.markdown.MarkdownBlock.ListBlock.UnorderedList
import org.jetbrains.jewel.markdown.MarkdownBlock.ListItem
import org.jetbrains.jewel.markdown.extensions.MarkdownBlockRendererExtension
import org.jetbrains.jewel.markdown.rendering.InlineMarkdownRenderer
import org.jetbrains.jewel.markdown.rendering.MarkdownBlockRenderer

/**
 * Renders a [FrontMatter] block as a headerless two-column [BasicTableLayout]: keys in the first column, values in the
 * second.
 *
 * @param styling The styling to use for the front matter table.
 */
@ApiStatus.Experimental
@ExperimentalJewelApi
internal class FrontMatterBlockRenderer(private val styling: FrontMatterStyling) : MarkdownBlockRendererExtension {
    override fun canRender(block: CustomBlock): Boolean = block is FrontMatter

    @Suppress("LambdaParameterEventTrailing")
    @Composable
    override fun RenderCustomBlock(
        block: CustomBlock,
        blockRenderer: MarkdownBlockRenderer,
        inlineRenderer: InlineMarkdownRenderer,
        enabled: Boolean,
        modifier: Modifier,
        onUrlClick: (String) -> Unit,
    ) {
        val frontMatter = block as FrontMatter

        val rows =
            remember(frontMatter, blockRenderer, styling, enabled, onUrlClick) {
                frontMatter.entries.map<FrontMatter.Entry, List<@Composable () -> Unit>> { entry ->
                    val keyContent = textAsCellContent(entry.key)
                    val valueContent = createValueContent(entry.value)
                    listOf(
                        { Cell(keyContent, blockRenderer, enabled, onUrlClick) },
                        { Cell(valueContent, blockRenderer, enabled, onUrlClick) },
                    )
                }
            }

        BasicTableLayout(
            rowCount = frontMatter.entries.size,
            columnCount = 2,
            cellBorderColor = styling.colors.borderColor,
            cellBorderWidth = styling.metrics.borderWidth,
            rows = rows,
            modifier = modifier,
        )
    }

    @Composable
    private fun Cell(
        content: MarkdownBlock,
        blockRenderer: MarkdownBlockRenderer,
        enabled: Boolean,
        onUrlClick: (String) -> Unit,
    ) {
        Box(
            modifier =
                Modifier.background(styling.colors.background).padding(styling.metrics.cellPadding).clipToBounds(),
            contentAlignment = Alignment.TopStart,
        ) {
            blockRenderer.RenderBlock(content, enabled, onUrlClick, Modifier)
        }
    }
}

internal fun createValueContent(entryValue: FrontMatter.Value): MarkdownBlock =
    when (entryValue) {
        is FrontMatter.Value.ListValue -> {
            UnorderedList(
                children = entryValue.items.map { value -> ListItem(textAsCellContent(value)) },
                isTight = true,
                marker = "-",
            )
        }
        is FrontMatter.Value.Scalar -> textAsCellContent(entryValue.text)
    }

private fun textAsCellContent(value: String): MarkdownBlock =
    MarkdownBlock.Paragraph(listOf(InlineMarkdown.Text(value)))
