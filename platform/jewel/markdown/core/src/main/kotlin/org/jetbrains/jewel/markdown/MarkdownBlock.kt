package org.jetbrains.jewel.markdown

import org.jetbrains.annotations.ApiStatus
import org.jetbrains.jewel.foundation.ExperimentalJewelApi
import org.jetbrains.jewel.foundation.GenerateDataFunctions
import org.jetbrains.jewel.foundation.code.MimeType

@ApiStatus.Experimental
@ExperimentalJewelApi
public sealed interface MarkdownBlock {
    @ApiStatus.Experimental
    @ExperimentalJewelApi
    @GenerateDataFunctions
    public class BlockQuote(override val children: List<MarkdownBlock>) : MarkdownBlock, WithChildBlocks {
        public constructor(vararg children: MarkdownBlock) : this(children.toList())

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false

            other as BlockQuote

            return children == other.children
        }

        override fun hashCode(): Int = children.hashCode()

        override fun toString(): String = "BlockQuote(children=$children)"
    }

    @ApiStatus.Experimental
    @ExperimentalJewelApi
    public sealed interface CodeBlock : MarkdownBlock {
        public val content: String

        @ApiStatus.Experimental
        @ExperimentalJewelApi
        @GenerateDataFunctions
        public class IndentedCodeBlock(override val content: String) : CodeBlock {
            override fun equals(other: Any?): Boolean {
                if (this === other) return true
                if (javaClass != other?.javaClass) return false

                other as IndentedCodeBlock

                return content == other.content
            }

            override fun hashCode(): Int = content.hashCode()

            override fun toString(): String = "IndentedCodeBlock(content='$content')"
        }

        @ApiStatus.Experimental
        @ExperimentalJewelApi
        @GenerateDataFunctions
        public class FencedCodeBlock(override val content: String, public val mimeType: MimeType?) : CodeBlock {
            override fun equals(other: Any?): Boolean {
                if (this === other) return true
                if (javaClass != other?.javaClass) return false

                other as FencedCodeBlock

                if (content != other.content) return false
                if (mimeType != other.mimeType) return false

                return true
            }

            override fun hashCode(): Int {
                var result = content.hashCode()
                result = 31 * result + (mimeType?.hashCode() ?: 0)
                return result
            }

            override fun toString(): String = "FencedCodeBlock(content='$content', mimeType=$mimeType)"
        }
    }

    @ApiStatus.Experimental @ExperimentalJewelApi public interface CustomBlock : MarkdownBlock

    @ApiStatus.Experimental
    @ExperimentalJewelApi
    @GenerateDataFunctions
    public class Heading(override val inlineContent: List<InlineMarkdown>, public val level: Int) :
        MarkdownBlock, WithInlineMarkdown {
        public constructor(level: Int, vararg inlineContent: InlineMarkdown) : this(inlineContent.toList(), level)

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false

            other as Heading

            if (level != other.level) return false
            if (inlineContent != other.inlineContent) return false

            return true
        }

        override fun hashCode(): Int {
            var result = level
            result = 31 * result + inlineContent.hashCode()
            return result
        }

        override fun toString(): String = "Heading(inlineContent=$inlineContent, level=$level)"
    }

    @ApiStatus.Experimental
    @ExperimentalJewelApi
    @GenerateDataFunctions
    public class HtmlBlock(public val content: String) : MarkdownBlock {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false

            other as HtmlBlock

            return content == other.content
        }

        override fun hashCode(): Int = content.hashCode()

        override fun toString(): String = "HtmlBlock(content='$content')"
    }

    @ApiStatus.Experimental
    @ExperimentalJewelApi
    public sealed interface ListBlock : MarkdownBlock, WithChildBlocks {
        override val children: List<ListItem>
        public val isTight: Boolean

        @ApiStatus.Experimental
        @ExperimentalJewelApi
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

            override fun equals(other: Any?): Boolean {
                if (this === other) return true
                if (javaClass != other?.javaClass) return false

                other as OrderedList

                if (isTight != other.isTight) return false
                if (startFrom != other.startFrom) return false
                if (children != other.children) return false
                if (delimiter != other.delimiter) return false

                return true
            }

            override fun hashCode(): Int {
                var result = isTight.hashCode()
                result = 31 * result + startFrom
                result = 31 * result + children.hashCode()
                result = 31 * result + delimiter.hashCode()
                return result
            }

            override fun toString(): String {
                return "OrderedList(" +
                    "children=$children, " +
                    "isTight=$isTight, " +
                    "startFrom=$startFrom, " +
                    "delimiter='$delimiter'" +
                    ")"
            }
        }

        @ApiStatus.Experimental
        @ExperimentalJewelApi
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

            override fun equals(other: Any?): Boolean {
                if (this === other) return true
                if (javaClass != other?.javaClass) return false

                other as UnorderedList

                if (isTight != other.isTight) return false
                if (children != other.children) return false
                if (marker != other.marker) return false

                return true
            }

            override fun hashCode(): Int {
                var result = isTight.hashCode()
                result = 31 * result + children.hashCode()
                result = 31 * result + marker.hashCode()
                return result
            }

            override fun toString(): String = "UnorderedList(children=$children, isTight=$isTight, marker='$marker')"
        }
    }

    @ApiStatus.Experimental
    @ExperimentalJewelApi
    @GenerateDataFunctions
    public class ListItem(override val children: List<MarkdownBlock>, public val level: Int) :
        MarkdownBlock, WithChildBlocks {
        public constructor(vararg children: MarkdownBlock, level: Int) : this(children.toList(), level)

        public constructor(vararg children: MarkdownBlock) : this(children.toList(), 0)

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false

            other as ListItem

            if (level != other.level) return false
            if (children != other.children) return false

            return true
        }

        override fun hashCode(): Int {
            var result = level
            result = 31 * result + children.hashCode()
            return result
        }

        override fun toString(): String = "ListItem(children=$children, level=$level)"
    }

    @ApiStatus.Experimental @ExperimentalJewelApi public data object ThematicBreak : MarkdownBlock

    @ApiStatus.Experimental
    @ExperimentalJewelApi
    @GenerateDataFunctions
    public class Paragraph(override val inlineContent: List<InlineMarkdown>) : MarkdownBlock, WithInlineMarkdown {
        public constructor(vararg inlineContent: InlineMarkdown) : this(inlineContent.toList())

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false

            other as Paragraph

            return inlineContent == other.inlineContent
        }

        override fun hashCode(): Int = inlineContent.hashCode()

        override fun toString(): String = "Paragraph(inlineContent=$inlineContent)"
    }
}
