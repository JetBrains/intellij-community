package org.jetbrains.jewel.markdown

import org.jetbrains.jewel.foundation.GenerateDataFunctions
import org.jetbrains.jewel.foundation.code.MimeType

public sealed interface MarkdownBlock {
    @GenerateDataFunctions
    public class BlockQuote(public val children: List<MarkdownBlock>) : MarkdownBlock {
        public constructor(vararg children: MarkdownBlock) : this(children.toList())
    }

    public sealed interface CodeBlock : MarkdownBlock {
        public val content: String

        @GenerateDataFunctions public class IndentedCodeBlock(override val content: String) : CodeBlock

        @GenerateDataFunctions
        public class FencedCodeBlock(override val content: String, public val mimeType: MimeType?) : CodeBlock
    }

    public interface CustomBlock : MarkdownBlock

    @GenerateDataFunctions
    public class Heading(override val inlineContent: List<InlineMarkdown>, public val level: Int) :
        MarkdownBlock, WithInlineMarkdown {
        public constructor(level: Int, vararg inlineContent: InlineMarkdown) : this(inlineContent.toList(), level)
    }

    @GenerateDataFunctions public class HtmlBlock(public val content: String) : MarkdownBlock

    public sealed interface ListBlock : MarkdownBlock {
        public val children: List<ListItem>
        public val isTight: Boolean

        @GenerateDataFunctions
        public class OrderedList(
            override val children: List<ListItem>,
            override val isTight: Boolean,
            public val startFrom: Int,
            public val delimiter: String,
        ) : ListBlock {
            public constructor(
                isTight: Boolean,
                startFrom: Int,
                delimiter: String,
                vararg children: ListItem,
            ) : this(children.toList(), isTight, startFrom, delimiter)
        }

        @GenerateDataFunctions
        public class UnorderedList(
            override val children: List<ListItem>,
            override val isTight: Boolean,
            public val marker: String,
        ) : ListBlock {
            public constructor(
                isTight: Boolean,
                marker: String,
                vararg children: ListItem,
            ) : this(children.toList(), isTight, marker)
        }
    }

    @GenerateDataFunctions
    public class ListItem(public val children: List<MarkdownBlock>) : MarkdownBlock {
        public constructor(vararg children: MarkdownBlock) : this(children.toList())
    }

    public data object ThematicBreak : MarkdownBlock

    @GenerateDataFunctions
    public class Paragraph(override val inlineContent: List<InlineMarkdown>) : MarkdownBlock, WithInlineMarkdown {
        public constructor(vararg inlineContent: InlineMarkdown) : this(inlineContent.toList())
    }
}
