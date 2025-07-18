package org.jetbrains.jewel.markdown.extensions.github.tables

import androidx.compose.ui.Alignment
import org.commonmark.ext.gfm.tables.TableBlock as CommonMarkTableBlock
import org.commonmark.ext.gfm.tables.TableBody as CommonMarkTableBody
import org.commonmark.ext.gfm.tables.TableCell as CommonMarkTableCell
import org.commonmark.ext.gfm.tables.TableCell.Alignment.CENTER
import org.commonmark.ext.gfm.tables.TableCell.Alignment.LEFT
import org.commonmark.ext.gfm.tables.TableCell.Alignment.RIGHT
import org.commonmark.ext.gfm.tables.TableHead as CommonMarkTableHeader
import org.commonmark.ext.gfm.tables.TableRow as CommonMarkTableRow
import org.commonmark.ext.gfm.tables.TablesExtension
import org.commonmark.node.CustomBlock
import org.commonmark.node.Node
import org.commonmark.parser.Parser.Builder
import org.commonmark.parser.Parser.ParserExtension
import org.commonmark.renderer.text.TextContentRenderer
import org.commonmark.renderer.text.TextContentRenderer.TextContentRendererExtension
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.jewel.foundation.ExperimentalJewelApi
import org.jetbrains.jewel.markdown.InlineMarkdown
import org.jetbrains.jewel.markdown.MarkdownBlock
import org.jetbrains.jewel.markdown.extensions.MarkdownBlockProcessorExtension
import org.jetbrains.jewel.markdown.extensions.MarkdownBlockRendererExtension
import org.jetbrains.jewel.markdown.extensions.MarkdownProcessorExtension
import org.jetbrains.jewel.markdown.extensions.MarkdownRendererExtension
import org.jetbrains.jewel.markdown.processing.MarkdownProcessor
import org.jetbrains.jewel.markdown.processing.readInlineMarkdown
import org.jetbrains.jewel.markdown.rendering.MarkdownStyling

/**
 * Adds support for table parsing. Tables are a GitHub Flavored Markdown extension, defined
 * [in the GFM specs](https://github.github.com/gfm/#tables-extension-).
 *
 * @see TablesExtension
 */
@ApiStatus.Experimental
@ExperimentalJewelApi
public object GitHubTableProcessorExtension : MarkdownProcessorExtension {
    override val parserExtension: ParserExtension = GitHubTablesCommonMarkExtension
    override val textRendererExtension: TextContentRendererExtension = GitHubTablesCommonMarkExtension

    override val blockProcessorExtension: MarkdownBlockProcessorExtension = GitHubTablesProcessorExtension

    private object GitHubTablesProcessorExtension : MarkdownBlockProcessorExtension {
        override fun canProcess(block: CustomBlock): Boolean = block is CommonMarkTableBlock

        override fun processMarkdownBlock(
            block: CustomBlock,
            processor: MarkdownProcessor,
        ): MarkdownBlock.CustomBlock? {
            val tableBlock = block as CommonMarkTableBlock
            val header = tableBlock.firstChild as? CommonMarkTableHeader ?: return null

            val body = tableBlock.lastChild as? CommonMarkTableBody

            return try {
                TableBlock(
                    TableHeader(
                        // The header contains only one CommonMarkTableRow
                        (header.firstChild as CommonMarkTableRow).mapCellsIndexed { columnIndex, cell ->
                            TableCell(
                                rowIndex = 0,
                                columnIndex = columnIndex,
                                content = cell.readInlineMarkdown(processor),
                                alignment = getAlignment(cell),
                            )
                        }
                    ),
                    body
                        ?.mapRowsIndexed { rowIndex, row ->
                            TableRow(
                                rowIndex = rowIndex,
                                row.mapCellsIndexed { columnIndex, cell ->
                                    TableCell(
                                        rowIndex = rowIndex + 1, // The header is row zero
                                        columnIndex = columnIndex,
                                        content = cell.readInlineMarkdown(processor),
                                        alignment = getAlignment(cell),
                                    )
                                },
                            )
                        }
                        .orEmpty(),
                )
            } catch (_: IllegalArgumentException) {
                null
            }
        }

        private fun getAlignment(cell: CommonMarkTableCell) =
            when (cell.alignment) {
                LEFT -> Alignment.Start
                CENTER -> Alignment.CenterHorizontally
                RIGHT -> Alignment.End
                null -> null
            }

        private inline fun CommonMarkTableRow.mapCellsIndexed(
            mapper: (Int, CommonMarkTableCell) -> TableCell
        ): List<TableCell> = buildList {
            forEachChildIndexed { index, child -> if (child is CommonMarkTableCell) add(mapper(index, child)) }
        }

        private inline fun CommonMarkTableBody.mapRowsIndexed(
            mapper: (Int, CommonMarkTableRow) -> TableRow
        ): List<TableRow> = buildList {
            forEachChildIndexed { index, child -> if (child is CommonMarkTableRow) add(mapper(index, child)) }
        }

        private inline fun Node.forEachChildIndexed(action: (Int, Node) -> Unit) {
            var child = firstChild

            var index = 0
            while (child != null) {
                action(index, child)
                index++
                child = child.next
            }
        }
    }
}

private object GitHubTablesCommonMarkExtension : ParserExtension, TextContentRendererExtension {
    override fun extend(parserBuilder: Builder) {
        parserBuilder.extensions(listOf(TablesExtension.create()))
    }

    override fun extend(rendererBuilder: TextContentRenderer.Builder) {
        rendererBuilder.extensions(listOf(TablesExtension.create()))
    }
}

@ApiStatus.Experimental
@ExperimentalJewelApi
public class GitHubTableRendererExtension(tableStyling: GfmTableStyling, rootStyling: MarkdownStyling) :
    MarkdownRendererExtension {
    override val blockRenderer: MarkdownBlockRendererExtension = GitHubTableBlockRenderer(rootStyling, tableStyling)
}

internal data class TableBlock(val header: TableHeader, val rows: List<TableRow>) : MarkdownBlock.CustomBlock {
    val rowCount: Int = rows.size + 1 // We always have a header
    val columnCount: Int

    init {
        require(header.cells.isNotEmpty()) { "Header cannot be empty" }
        val headerColumns = header.cells.size

        if (rows.isNotEmpty()) {
            val bodyColumns = rows.first().cells.size
            require(rows.all { it.cells.size == bodyColumns }) { "Inconsistent cell count in table body" }
            require(headerColumns == bodyColumns) { "Inconsistent cell count between table body and header" }
        }

        columnCount = headerColumns
    }
}

internal data class TableHeader(val cells: List<TableCell>) : MarkdownBlock.CustomBlock

internal data class TableRow(val rowIndex: Int, val cells: List<TableCell>) : MarkdownBlock.CustomBlock

internal data class TableCell(
    val rowIndex: Int,
    val columnIndex: Int,
    val content: List<InlineMarkdown>,
    val alignment: Alignment.Horizontal?,
) : MarkdownBlock.CustomBlock
