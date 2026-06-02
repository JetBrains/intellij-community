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
import org.jetbrains.jewel.markdown.MarkdownBlock.ListBlock.UnorderedList
import org.jetbrains.jewel.markdown.MarkdownBlock.ListItem
import org.jetbrains.jewel.markdown.extensions.MarkdownBlockProcessorExtension
import org.jetbrains.jewel.markdown.extensions.MarkdownProcessorExtension
import org.jetbrains.jewel.markdown.extensions.github.tables.TableBlock
import org.jetbrains.jewel.markdown.extensions.github.tables.TableCell
import org.jetbrains.jewel.markdown.extensions.github.tables.TableRow
import org.jetbrains.jewel.markdown.processing.MarkdownProcessor

/**
 * Adds support for YAML front matter metadata blocks. Front matter is a common way to add metadata to Markdown
 * documents, delimited by `---` markers at the beginning of the file.
 *
 * Front matter metadata is rendered as a headerless two-column table where the first column contains the keys and the
 * second column contains the values. When a value is a list, it is rendered as an unordered list within the value cell.
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

            val rows =
                entries.mapIndexed { rowIndex, entry ->
                    TableRow(
                        rowIndex = rowIndex,
                        cells =
                            listOf(
                                TableCell(
                                    rowIndex = rowIndex,
                                    columnIndex = 0,
                                    content = textAsCellContent(entry.key),
                                    alignment = null,
                                ),
                                TableCell(
                                    rowIndex = rowIndex,
                                    columnIndex = 1,
                                    content = createValueContent(entry),
                                    alignment = null,
                                ),
                            ),
                    )
                }

            return TableBlock(header = null, rows = rows)
        }

        private fun createValueContent(entry: FrontMatterNode): MarkdownBlock =
            if (entry.isList) {
                UnorderedList(
                    children = entry.values.map { value -> ListItem(textAsCellContent(value)) },
                    isTight = true,
                    marker = "-",
                )
            } else {
                textAsCellContent(entry.values.firstOrNull().orEmpty())
            }

        private fun textAsCellContent(value: String): MarkdownBlock =
            MarkdownBlock.Paragraph(listOf(InlineMarkdown.Text(value)))

        private fun FrontMatterBlock.collectEntries(): List<FrontMatterNode> = buildList {
            forEachChild { child ->
                if (child is FrontMatterNode) {
                    add(child)
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
