package org.jetbrains.jewel.markdown

import org.commonmark.node.Block
import org.commonmark.node.Heading as CMHeading
import org.commonmark.node.Paragraph as CMParagraph

public sealed interface MarkdownBlock {

    public data class BlockQuote(val children: List<MarkdownBlock>) : MarkdownBlock

    public sealed interface CodeBlock : MarkdownBlock {

        public val content: String

        public data class IndentedCodeBlock(
            override val content: String,
        ) : CodeBlock

        public data class FencedCodeBlock(
            override val content: String,
            val mimeType: MimeType?,
        ) : CodeBlock
    }

    public interface CustomBlock : MarkdownBlock

    @JvmInline
    public value class Heading(
        private val nativeBlock: CMHeading,
    ) : MarkdownBlock, BlockWithInlineMarkdown {

        override val inlineContent: Iterable<InlineMarkdown>
            get() = nativeBlock.inlineContent()

        public val level: Int
            get() = nativeBlock.level
    }

    public data class HtmlBlock(val content: String) : MarkdownBlock

    public sealed interface ListBlock : MarkdownBlock {

        public val children: List<ListItem>
        public val isTight: Boolean

        public data class OrderedList(
            override val children: List<ListItem>,
            override val isTight: Boolean,
            val startFrom: Int,
            val delimiter: String,
        ) : ListBlock

        public data class UnorderedList(
            override val children: List<ListItem>,
            override val isTight: Boolean,
            val marker: String,
        ) : ListBlock
    }

    public data class ListItem(
        val children: List<MarkdownBlock>,
    ) : MarkdownBlock

    public object ThematicBreak : MarkdownBlock

    @JvmInline
    public value class Paragraph(private val nativeBlock: CMParagraph) : MarkdownBlock, BlockWithInlineMarkdown {

        override val inlineContent: Iterable<InlineMarkdown>
            get() = nativeBlock.inlineContent()
    }
}

public interface BlockWithInlineMarkdown {

    public val inlineContent: Iterable<InlineMarkdown>
}

private fun Block.inlineContent(): Iterable<InlineMarkdown> =
    object : Iterable<InlineMarkdown> {
        override fun iterator(): Iterator<InlineMarkdown> =
            object : Iterator<InlineMarkdown> {
                var current = this@inlineContent.firstChild

                override fun hasNext(): Boolean = current != null

                override fun next(): InlineMarkdown =
                    if (hasNext()) {
                        current.toInlineNode().also {
                            current = current.next
                        }
                    } else {
                        throw NoSuchElementException()
                    }
            }
    }
