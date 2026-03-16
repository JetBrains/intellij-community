package org.jetbrains.jewel.markdown.extensions.github.tables

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.jewel.foundation.ExperimentalJewelApi
import org.jetbrains.jewel.foundation.layout.BasicTableLayout
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.markdown.MarkdownBlock.CustomBlock
import org.jetbrains.jewel.markdown.extensions.MarkdownBlockRendererExtension
import org.jetbrains.jewel.markdown.rendering.InlineMarkdownRenderer
import org.jetbrains.jewel.markdown.rendering.InlinesStyling
import org.jetbrains.jewel.markdown.rendering.MarkdownBlockRenderer
import org.jetbrains.jewel.markdown.rendering.MarkdownStyling

/**
 * Renders a [TableBlock] as a [BasicTableLayout].
 *
 * @param rootStyling The root styling to use.
 * @param tableStyling The styling to use for the table.
 */
@ApiStatus.Experimental
@ExperimentalJewelApi
internal class GitHubTableBlockRenderer(
    private val rootStyling: MarkdownStyling,
    private val tableStyling: GfmTableStyling,
) : MarkdownBlockRendererExtension {
    override fun canRender(block: CustomBlock): Boolean = block is TableBlock

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
        val tableBlock = block as TableBlock

        // Headers usually have a tweaked font weight
        val headerRootStyling =
            remember(JewelTheme.instanceUuid, blockRenderer, tableStyling.headerBaseFontWeight) {
                val rootStyling = blockRenderer.rootStyling
                val semiboldInlinesStyling =
                    rootStyling.paragraph.inlinesStyling.withFontWeight(tableStyling.headerBaseFontWeight)

                // Header cells produced by table parsing are represented as Paragraph blocks,
                // so overriding Paragraph styling is enough here.
                MarkdownStyling(
                    rootStyling.blockVerticalSpacing,
                    MarkdownStyling.Paragraph(semiboldInlinesStyling),
                    rootStyling.heading,
                    rootStyling.blockQuote,
                    rootStyling.code,
                    rootStyling.list,
                    rootStyling.image,
                    rootStyling.thematicBreak,
                    rootStyling.htmlBlock,
                )
            }

        val headerRenderer = remember(headerRootStyling) { blockRenderer.createCopy(rootStyling = headerRootStyling) }

        val rows =
            remember(tableBlock, blockRenderer, inlineRenderer, tableStyling) {
                buildList<List<@Composable () -> Unit>> {
                    if (tableBlock.header != null) {
                        add(headerCells(tableBlock.header, headerRenderer, enabled, onUrlClick))
                    }

                    val rowsCells =
                        tableBlock.rows.map<TableRow, List<@Composable () -> Unit>> { row ->
                            rowCells(row, blockRenderer, enabled, onUrlClick)
                        }
                    addAll(rowsCells)
                }
            }

        BasicTableLayout(
            rowCount = tableBlock.rowCount,
            columnCount = tableBlock.columnCount,
            cellBorderColor = tableStyling.colors.borderColor,
            cellBorderWidth = tableStyling.metrics.borderWidth,
            rows = rows,
            modifier = modifier,
        )
    }

    private fun headerCells(
        header: TableHeader,
        headerRenderer: MarkdownBlockRenderer,
        enabled: Boolean,
        onUrlClick: (String) -> Unit,
    ): List<@Composable (() -> Unit)> =
        header.cells.map {
            {
                Cell(
                    cell = it,
                    backgroundColor = tableStyling.colors.rowBackgroundColor,
                    padding = tableStyling.metrics.cellPadding,
                    defaultAlignment = tableStyling.metrics.headerDefaultCellContentAlignment,
                    blockRenderer = headerRenderer,
                    enabled = enabled,
                    onUrlClick = onUrlClick,
                )
            }
        }

    private fun rowCells(
        row: TableRow,
        blockRenderer: MarkdownBlockRenderer,
        enabled: Boolean,
        onUrlClick: (String) -> Unit,
    ): List<@Composable (() -> Unit)> =
        row.cells.map<TableCell, @Composable () -> Unit> { cell ->
            {
                val backgroundColor =
                    if (tableStyling.colors.rowBackgroundStyle == RowBackgroundStyle.Striped) {
                        if (cell.rowIndex % 2 == 1) {
                            tableStyling.colors.alternateRowBackgroundColor
                        } else {
                            tableStyling.colors.rowBackgroundColor
                        }
                    } else {
                        tableStyling.colors.rowBackgroundColor
                    }

                Cell(
                    cell = cell,
                    backgroundColor = backgroundColor,
                    padding = tableStyling.metrics.cellPadding,
                    defaultAlignment = tableStyling.metrics.defaultCellContentAlignment,
                    blockRenderer = blockRenderer,
                    enabled = enabled,
                    onUrlClick = onUrlClick,
                )
            }
        }

    private fun InlinesStyling.withFontWeight(newFontWeight: FontWeight) =
        InlinesStyling(
            textStyle = textStyle.copy(fontWeight = newFontWeight),
            inlineCode = inlineCode.copy(fontWeight = newFontWeight),
            link = link.copy(fontWeight = newFontWeight),
            linkDisabled = linkDisabled.copy(fontWeight = newFontWeight),
            linkHovered = linkHovered.copy(fontWeight = newFontWeight),
            linkFocused = linkFocused.copy(fontWeight = newFontWeight),
            linkPressed = linkPressed.copy(fontWeight = newFontWeight),
            linkVisited = linkVisited.copy(fontWeight = newFontWeight),
            emphasis = emphasis.copy(fontWeight = newFontWeight),
            strongEmphasis = strongEmphasis.copy(fontWeight = newFontWeight),
            inlineHtml = inlineHtml.copy(fontWeight = newFontWeight),
        )

    @Composable
    private fun Cell(
        cell: TableCell,
        backgroundColor: Color,
        padding: PaddingValues,
        defaultAlignment: Alignment.Horizontal,
        blockRenderer: MarkdownBlockRenderer,
        enabled: Boolean,
        onUrlClick: (String) -> Unit,
    ) {
        Box(
            modifier = Modifier.background(backgroundColor).padding(padding).clipToBounds(),
            contentAlignment = (cell.alignment ?: defaultAlignment).asContentAlignment(),
        ) {
            blockRenderer.RenderBlock(cell.content, enabled, onUrlClick, Modifier)
        }
    }

    private fun Alignment.Horizontal.asContentAlignment() =
        when (this) {
            Alignment.Start -> Alignment.TopStart
            Alignment.CenterHorizontally -> Alignment.TopCenter
            Alignment.End -> Alignment.TopEnd
            else -> error("Unsupported alignment: $this")
        }
}
