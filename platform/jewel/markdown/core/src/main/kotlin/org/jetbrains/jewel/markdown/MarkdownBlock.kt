package org.jetbrains.jewel.markdown

public sealed interface MarkdownBlock {

    public data class BlockQuote(val content: List<MarkdownBlock>) : MarkdownBlock

    public interface CustomBlock : MarkdownBlock

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

    public data class Heading(
        override val inlineContent: List<InlineMarkdown>,
        val level: Int,
    ) : MarkdownBlock, BlockWithInlineMarkdown

    public data class HtmlBlock(val content: String) : MarkdownBlock

    public sealed interface ListBlock : MarkdownBlock {

        public val items: List<ListItem>
        public val isTight: Boolean

        public data class BulletList(
            override val items: List<ListItem>,
            override val isTight: Boolean,
            val bulletMarker: String,
        ) : ListBlock

        public data class OrderedList(
            override val items: List<ListItem>,
            override val isTight: Boolean,
            val startFrom: Int,
            val delimiter: String,
        ) : ListBlock
    }

    public data class ListItem(
        val content: List<MarkdownBlock>,
    ) : MarkdownBlock

    public object ThematicBreak : MarkdownBlock

    public data class Paragraph(override val inlineContent: List<InlineMarkdown>) :
        MarkdownBlock, BlockWithInlineMarkdown
}

public interface BlockWithInlineMarkdown {

    public val inlineContent: List<InlineMarkdown>
}
