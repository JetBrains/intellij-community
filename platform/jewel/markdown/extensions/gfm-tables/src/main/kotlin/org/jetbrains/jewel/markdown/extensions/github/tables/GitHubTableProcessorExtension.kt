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
import org.jetbrains.jewel.markdown.extensions.MarkdownHtmlConverterExtension
import org.jetbrains.jewel.markdown.extensions.MarkdownProcessorExtension
import org.jetbrains.jewel.markdown.processing.MarkdownProcessor
import org.jetbrains.jewel.markdown.processing.html.HtmlElementConverter
import org.jetbrains.jewel.markdown.processing.html.MarkdownHtmlNode
import org.jetbrains.jewel.markdown.processing.readInlineMarkdown

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

    override val htmlConverterExtension: MarkdownHtmlConverterExtension = GitHubTablesHtmlConverterExtension

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

private object GitHubTablesHtmlConverterExtension : MarkdownHtmlConverterExtension {
    override val supportedTags: Set<String>
        get() = setOf("table")

    override fun provideConverter(tagName: String): HtmlElementConverter = GitHubTableHtmlElementConverter

    private object GitHubTableHtmlElementConverter : HtmlElementConverter {
        override fun convert(
            htmlElement: MarkdownHtmlNode.Element,
            convertChildren: MarkdownHtmlNode.Element.() -> List<MarkdownBlock>,
            convertInlines: (List<MarkdownHtmlNode>) -> List<InlineMarkdown>,
        ): MarkdownBlock? {
            val tbody =
                htmlElement.children.filterIsInstance<MarkdownHtmlNode.Element>().find { it.tag == "tbody" }
                    ?: return null
            val htmlRows = tbody.children.filterIsInstance<MarkdownHtmlNode.Element>().filter { it.tag == "tr" }
            if (htmlRows.isEmpty()) return null
            val markdownRows: List<MutableList<TableCell>> = buildList {
                for (i in 0..htmlRows.lastIndex) {
                    add(
                        htmlRows[i]
                            .rowElements()
                            .mapIndexed { index, rowCell ->
                                val inlines = convertInlines(rowCell.children)
                                if (inlines.isEmpty()) return@mapIndexed TableCell(i, index, emptyList(), null)
                                TableCell(rowIndex = i, columnIndex = index, content = inlines, alignment = null)
                            }
                            .toMutableList()
                    )
                }
            }
            val maxColumns = markdownRows.maxOf { it.size }
            for ((rowIndex, row) in markdownRows.withIndex()) {
                for (columnIndex in row.size until maxColumns) {
                    row.add(TableCell(rowIndex, columnIndex, emptyList(), null))
                }
            }
            val header = TableHeader(markdownRows.first())
            val rows = markdownRows.drop(1).mapIndexed(::TableRow)
            return try {
                TableBlock(header, rows)
            } catch (_: IllegalArgumentException) {
                null
            }
        }

        // html allows mixing header (<th>) and row (<td>) elements deliberately,
        // so each of them can be used in any row
        private fun MarkdownHtmlNode.Element.rowElements(): List<MarkdownHtmlNode.Element> =
            children.filterIsInstance<MarkdownHtmlNode.Element>().filter { it.tag == "td" || it.tag == "th" }
    }
}
