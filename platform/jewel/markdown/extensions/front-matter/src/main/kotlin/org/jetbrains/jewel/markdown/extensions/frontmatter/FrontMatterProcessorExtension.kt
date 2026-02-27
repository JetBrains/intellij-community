package org.jetbrains.jewel.markdown.extensions.frontmatter

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
import org.jetbrains.jewel.markdown.extensions.MarkdownProcessorExtension
import org.jetbrains.jewel.markdown.extensions.github.tables.TableBlock
import org.jetbrains.jewel.markdown.extensions.github.tables.TableCell
import org.jetbrains.jewel.markdown.extensions.github.tables.TableHeader
import org.jetbrains.jewel.markdown.extensions.github.tables.TableRow
import org.jetbrains.jewel.markdown.processing.MarkdownProcessor

/**
 * Adds support for YAML front matter metadata blocks. Front matter is a common way to add metadata to Markdown
 * documents, delimited by `---` markers at the beginning of the file.
 *
 * Front matter metadata is rendered as a two-row table: the first row (header) contains the keys and the second row
 * contains the values. When a value is a list, it is rendered as a single-row nested table within the cell.
 */
@ApiStatus.Experimental
@ExperimentalJewelApi
public object FrontMatterProcessorExtension : MarkdownProcessorExtension {
    override val parserExtension: ParserExtension = FrontMatterParserExtension
    override val textRendererExtension: TextContentRendererExtension = FrontMatterParserExtension

    override val blockProcessorExtension: MarkdownBlockProcessorExtension = FrontMatterBlockProcessorExtension

    private object FrontMatterBlockProcessorExtension : MarkdownBlockProcessorExtension {
        override fun canProcess(block: CustomBlock): Boolean = block is FrontMatterBlock

        override fun processMarkdownBlock(
            block: CustomBlock,
            processor: MarkdownProcessor,
        ): MarkdownBlock.CustomBlock? {
            val frontMatterBlock = block as FrontMatterBlock
            val entries = frontMatterBlock.collectEntries()
            if (entries.isEmpty()) return null

            val headerCells =
                entries.mapIndexed { index, (key, _) ->
                    TableCell(rowIndex = 0, columnIndex = index, content = textAsCellContent(key), alignment = null)
                }

            val valueCells =
                entries.mapIndexed { index, (_, values) -> createValueCell(columnIndex = index, values = values) }
            val dataRow = TableRow(rowIndex = 0, cells = valueCells)

            return TableBlock(header = TableHeader(headerCells), rows = listOf(dataRow))
        }

        private fun createValueCell(columnIndex: Int, values: List<String>): TableCell =
            if (values.size <= 1) {
                TableCell(
                    rowIndex = 1,
                    columnIndex = columnIndex,
                    content = textAsCellContent(values.firstOrNull().orEmpty()),
                    alignment = null,
                )
            } else {
                // Multiple values are rendered as a headerless nested table (single data row)
                val valueCells =
                    values.mapIndexed { index, value ->
                        TableCell(
                            rowIndex = 0,
                            columnIndex = index,
                            content = textAsCellContent(value),
                            alignment = null,
                        )
                    }
                val nestedTable = TableBlock(header = null, rows = listOf(TableRow(rowIndex = 0, cells = valueCells)))
                TableCell(rowIndex = 1, columnIndex = columnIndex, content = nestedTable, alignment = null)
            }

        private fun textAsCellContent(value: String): MarkdownBlock =
            MarkdownBlock.Paragraph(listOf(InlineMarkdown.Text(value)))

        private fun FrontMatterBlock.collectEntries(): List<Pair<String, List<String>>> = buildList {
            forEachChild { child ->
                if (child is FrontMatterNode) {
                    add(child.key to child.values)
                }
            }
        }

        private inline fun Node.forEachChild(action: (Node) -> Unit) {
            var child = firstChild
            while (child != null) {
                action(child)
                child = child.next
            }
        }
    }
}

private object FrontMatterParserExtension : ParserExtension, TextContentRendererExtension {
    override fun extend(parserBuilder: Builder) {
        parserBuilder.customBlockParserFactory(FrontMatterBlockParser.Factory())
    }

    override fun extend(rendererBuilder: TextContentRenderer.Builder) {
        // No-op: front matter is not rendered as text
    }
}
