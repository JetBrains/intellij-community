package org.jetbrains.jewel.markdown.extensions.frontmatter

import org.commonmark.node.CustomBlock
import org.commonmark.node.Node
import org.commonmark.parser.Parser.Builder
import org.commonmark.parser.Parser.ParserExtension
import org.commonmark.renderer.NodeRenderer
import org.commonmark.renderer.text.TextContentNodeRendererContext
import org.commonmark.renderer.text.TextContentRenderer
import org.commonmark.renderer.text.TextContentRenderer.TextContentRendererExtension
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.jewel.foundation.ExperimentalJewelApi
import org.jetbrains.jewel.markdown.MarkdownBlock
import org.jetbrains.jewel.markdown.extensions.MarkdownBlockProcessorExtension
import org.jetbrains.jewel.markdown.extensions.MarkdownProcessorExtension
import org.jetbrains.jewel.markdown.processing.MarkdownProcessor

/**
 * Adds support for YAML front matter metadata blocks. Front matter is a common way to add metadata to Markdown
 * documents, delimited by `---` markers at the beginning of the file.
 *
 * Front matter metadata is emitted as a [FrontMatter] block. The [FrontMatterRendererExtension] renders it as a
 * headerless two-column table where the first column contains the keys and the second column contains the values. When
 * a value is a list, it is rendered as an unordered list within the value cell.
 *
 * A front matter block is only emitted when it is properly closed by a trailing `---` delimiter. If the opening `---`
 * is never closed (for example, because a Markdown construct interrupts it), the collected content is not treated as
 * front matter.
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
            // Only treat the collected content as front matter if it was closed by a trailing "---" delimiter.
            if (!frontMatterBlock.sawClosingDelimiter) return null

            val entries = frontMatterBlock.collectEntries()
            if (entries.isEmpty()) return null

            return FrontMatter(
                entries =
                    entries.map { entry ->
                        FrontMatter.Entry(key = entry.key, value = entry.values.mapToEntryValue(entry.isList))
                    }
            )
        }

        private fun List<String>.mapToEntryValue(isList: Boolean) =
            when (isList) {
                true -> FrontMatter.Value.ListValue(this)
                false -> FrontMatter.Value.Scalar(this.firstOrNull().orEmpty())
            }

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
        rendererBuilder.nodeRendererFactory { FrontMatterTextContentNodeRenderer(it) }
    }
}

/**
 * Renders a [FrontMatterBlock] to plain text for CommonMark's [TextContentRenderer] (text extraction, search, etc.).
 *
 * This is a **normalized reconstruction**, not the raw source. The parser keeps only the parsed key/values, so the
 * original text cannot be reproduced verbatim: the `---` delimiters, comments, quoting, block-scalar styling, and
 * block-list formatting are all discarded during parsing. We deliberately do **not** re-add the `---` fences, because
 * that would make the output look verbatim while the body is normalized. Reproducing the source exactly would require
 * the parser to retain the raw lines, which it currently does not.
 */
private class FrontMatterTextContentNodeRenderer(context: TextContentNodeRendererContext) : NodeRenderer {
    private val writer = context.writer

    override fun getNodeTypes(): Set<Class<out Node>> = setOf(FrontMatterBlock::class.java)

    override fun render(node: Node) {
        // Mirroring the Compose path: an unterminated block is not treated as front matter
        if (!(node as FrontMatterBlock).sawClosingDelimiter) return

        var child = node.firstChild
        while (child != null) {
            (child as? FrontMatterNode)?.let { entry ->
                writer.write(entry.key)
                writer.write(": ")
                writer.write(entry.values.joinToString(", "))
                writer.line()
            }
            child = child.next
        }
    }
}
