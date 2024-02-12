package org.jetbrains.jewel.markdown

import org.intellij.lang.annotations.Language

public sealed interface MarkdownBlock {

    public data class Paragraph(override val inlineContent: InlineMarkdown) :
        MarkdownBlock, BlockWithInlineMarkdown

    public sealed interface Heading : MarkdownBlock, BlockWithInlineMarkdown {

        public data class H1(override val inlineContent: InlineMarkdown) : Heading

        public data class H2(override val inlineContent: InlineMarkdown) : Heading

        public data class H3(override val inlineContent: InlineMarkdown) : Heading

        public data class H4(override val inlineContent: InlineMarkdown) : Heading

        public data class H5(override val inlineContent: InlineMarkdown) : Heading

        public data class H6(override val inlineContent: InlineMarkdown) : Heading
    }

    public data class BlockQuote(val content: List<MarkdownBlock>) : MarkdownBlock

    public sealed interface ListBlock : MarkdownBlock {

        public val items: List<ListItem>
        public val isTight: Boolean

        public data class OrderedList(
            override val items: List<ListItem>,
            override val isTight: Boolean,
            val startFrom: Int,
            val delimiter: Char,
        ) : ListBlock

        public data class UnorderedList(
            override val items: List<ListItem>,
            override val isTight: Boolean,
            val bulletMarker: Char,
        ) : ListBlock
    }

    public data class ListItem(
        val content: List<MarkdownBlock>,
    ) : MarkdownBlock

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

    public data class Image(val url: String, val altString: String?) : MarkdownBlock

    public object ThematicBreak : MarkdownBlock

    public data class HtmlBlock(val content: String) : MarkdownBlock

    public interface Extension : MarkdownBlock
}

public interface BlockWithInlineMarkdown {

    public val inlineContent: InlineMarkdown
}

/**
 * A run of inline Markdown used as content for
 * [block-level elements][MarkdownBlock].
 */
@JvmInline
public value class InlineMarkdown(@Language("Markdown") public val content: String)
