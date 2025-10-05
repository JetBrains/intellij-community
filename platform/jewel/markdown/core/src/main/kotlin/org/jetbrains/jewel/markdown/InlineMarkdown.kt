package org.jetbrains.jewel.markdown

import org.jetbrains.annotations.ApiStatus
import org.jetbrains.jewel.foundation.ExperimentalJewelApi
import org.jetbrains.jewel.foundation.GenerateDataFunctions

/**
 * An inline Markdown node, usually found as content for [block-level elements][MarkdownBlock] or other inline nodes
 * annotated with the [WithInlineMarkdown] interface.
 */
@ApiStatus.Experimental
@ExperimentalJewelApi
public sealed interface InlineMarkdown {
    @ApiStatus.Experimental
    @ExperimentalJewelApi
    @GenerateDataFunctions
    public class Code(override val content: String) : InlineMarkdown, WithTextContent {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false

            other as Code

            return content == other.content
        }

        override fun hashCode(): Int = content.hashCode()

        override fun toString(): String = "Code(content='$content')"
    }

    /**
     * An inline node that is delimited by a fixed delimiter, and can be rendered directly into an
     * [androidx.compose.ui.text.AnnotatedString].
     *
     * This type of node can be parsed by a
     * [org.jetbrains.jewel.markdown.extensions.MarkdownDelimitedInlineProcessorExtension] and rendered by a
     * [org.jetbrains.jewel.markdown.extensions.MarkdownDelimitedInlineRendererExtension].
     */
    @ApiStatus.Experimental
    @ExperimentalJewelApi
    public interface CustomDelimitedNode : InlineMarkdown, WithInlineMarkdown {
        /**
         * The string used to indicate the beginning of this type of inline node. Can be identical to the
         * [closingDelimiter].
         */
        public val openingDelimiter: String

        /**
         * The string used to indicate the end of this type of inline node. Can be identical to the [openingDelimiter].
         */
        public val closingDelimiter: String
            get() = openingDelimiter
    }

    @ApiStatus.Experimental
    @ExperimentalJewelApi
    @GenerateDataFunctions
    public class Emphasis(public val delimiter: String, override val inlineContent: List<InlineMarkdown>) :
        InlineMarkdown, WithInlineMarkdown {
        public constructor(
            delimiter: String,
            vararg inlineContent: InlineMarkdown,
        ) : this(delimiter, inlineContent.toList())

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false

            other as Emphasis

            if (delimiter != other.delimiter) return false
            if (inlineContent != other.inlineContent) return false

            return true
        }

        override fun hashCode(): Int {
            var result = delimiter.hashCode()
            result = 31 * result + inlineContent.hashCode()
            return result
        }

        override fun toString(): String = "Emphasis(delimiter='$delimiter', inlineContent=$inlineContent)"
    }

    public data object HardLineBreak : InlineMarkdown

    @ApiStatus.Experimental
    @ExperimentalJewelApi
    @GenerateDataFunctions
    public class HtmlInline(override val content: String) : InlineMarkdown, WithTextContent {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false

            other as HtmlInline

            return content == other.content
        }

        override fun hashCode(): Int = content.hashCode()

        override fun toString(): String = "HtmlInline(content='$content')"
    }

    @ApiStatus.Experimental
    @ExperimentalJewelApi
    @GenerateDataFunctions
    public class Image(
        public val source: String,
        public val alt: String,
        public val title: String?,
        override val inlineContent: List<InlineMarkdown>,
    ) : InlineMarkdown, WithInlineMarkdown {
        public constructor(
            source: String,
            alt: String,
            title: String?,
            vararg inlineContent: InlineMarkdown,
        ) : this(source, alt, title, inlineContent.toList())

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false

            other as Image

            if (source != other.source) return false
            if (alt != other.alt) return false
            if (title != other.title) return false
            if (inlineContent != other.inlineContent) return false

            return true
        }

        override fun hashCode(): Int {
            var result = source.hashCode()
            result = 31 * result + alt.hashCode()
            result = 31 * result + (title?.hashCode() ?: 0)
            result = 31 * result + inlineContent.hashCode()
            return result
        }

        override fun toString(): String {
            return "Image(" +
                "source='$source', " +
                "alt='$alt', " +
                "title=$title, " +
                "inlineContent=$inlineContent" +
                ")"
        }
    }

    @ApiStatus.Experimental
    @ExperimentalJewelApi
    @GenerateDataFunctions
    public class Link(
        public val destination: String,
        public val title: String?,
        override val inlineContent: List<InlineMarkdown>,
    ) : InlineMarkdown, WithInlineMarkdown {
        public constructor(
            destination: String,
            title: String?,
            vararg inlineContent: InlineMarkdown,
        ) : this(destination, title, inlineContent.toList())

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false

            other as Link

            if (destination != other.destination) return false
            if (title != other.title) return false
            if (inlineContent != other.inlineContent) return false

            return true
        }

        override fun hashCode(): Int {
            var result = destination.hashCode()
            result = 31 * result + (title?.hashCode() ?: 0)
            result = 31 * result + inlineContent.hashCode()
            return result
        }

        override fun toString(): String = "Link(destination='$destination', title=$title, inlineContent=$inlineContent)"
    }

    public data object SoftLineBreak : InlineMarkdown

    @ApiStatus.Experimental
    @ExperimentalJewelApi
    @GenerateDataFunctions
    public class StrongEmphasis(public val delimiter: String, override val inlineContent: List<InlineMarkdown>) :
        InlineMarkdown, WithInlineMarkdown {
        public constructor(
            delimiter: String,
            vararg inlineContent: InlineMarkdown,
        ) : this(delimiter, inlineContent.toList())

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false

            other as StrongEmphasis

            if (delimiter != other.delimiter) return false
            if (inlineContent != other.inlineContent) return false

            return true
        }

        override fun hashCode(): Int {
            var result = delimiter.hashCode()
            result = 31 * result + inlineContent.hashCode()
            return result
        }

        override fun toString(): String = "StrongEmphasis(delimiter='$delimiter', inlineContent=$inlineContent)"
    }

    @ApiStatus.Experimental
    @ExperimentalJewelApi
    @GenerateDataFunctions
    public class Text(override val content: String) : InlineMarkdown, WithTextContent {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false

            other as Text

            return content == other.content
        }

        override fun hashCode(): Int = content.hashCode()

        override fun toString(): String = "Text(content='$content')"
    }
}
