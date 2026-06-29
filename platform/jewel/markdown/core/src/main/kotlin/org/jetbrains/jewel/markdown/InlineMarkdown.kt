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
    /** An inline code span, rendered with a monospace font. */
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

    /** An inline emphasis (italic) node, delimited by [delimiter] and containing [inlineContent]. */
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

    /** A hard line break, rendered as a newline that forces a new line in the output. */
    public data object HardLineBreak : InlineMarkdown

    /** A raw inline HTML tag or entity, passed through as-is during rendering. */
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

    /**
     * An inline image node, corresponding to `![alt](source "title")` in Markdown.
     *
     * Standard renderer implementations apply the following sizing rules based on [width] and [height]:
     * - If you specify both sizes, the image is rendered at exactly those dimensions, stretching if the aspect ratio
     *   differs from the original.
     * - If you specify only one of them, the other dimension is scaled proportionally to preserve the aspect ratio.
     * - If you don't specify either, the image is rendered at its intrinsic (loaded) size.
     *
     * @param source The URL or path of the image.
     * @param alt The plain-text alternative description of the image.
     * @param title The optional tooltip title of the image.
     * @param inlineContent The parsed inline nodes that make up the alt text.
     * @param width The optional display width. See [DimensionSize] for supported value types.
     * @param height The optional display height. See [DimensionSize] for supported value types.
     */
    @ApiStatus.Experimental
    @ExperimentalJewelApi
    @GenerateDataFunctions
    public class Image(
        public val source: String,
        public val alt: String,
        public val title: String?,
        override val inlineContent: List<InlineMarkdown>,
        public val width: DimensionSize? = null,
        public val height: DimensionSize? = null,
    ) : InlineMarkdown, WithInlineMarkdown {
        public constructor(
            source: String,
            alt: String,
            title: String?,
            vararg inlineContent: InlineMarkdown,
            width: DimensionSize? = null,
            height: DimensionSize? = null,
        ) : this(source, alt, title, inlineContent.toList(), width, height)

        @Deprecated("Use a constructor with width and height parameters instead.", level = DeprecationLevel.HIDDEN)
        public constructor(
            source: String,
            alt: String,
            title: String?,
            vararg inlineContent: InlineMarkdown,
        ) : this(source, alt, title, inlineContent.toList(), null, null)

        @Deprecated("Use a constructor with width and height parameters instead.", level = DeprecationLevel.HIDDEN)
        public constructor(
            source: String,
            alt: String,
            title: String?,
            inlineContent: List<InlineMarkdown>,
        ) : this(source, alt, title, inlineContent, null, null)

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false

            other as Image

            if (source != other.source) return false
            if (alt != other.alt) return false
            if (title != other.title) return false
            if (inlineContent != other.inlineContent) return false
            if (width != other.width) return false
            if (height != other.height) return false

            return true
        }

        override fun hashCode(): Int {
            var result = source.hashCode()
            result = 31 * result + alt.hashCode()
            result = 31 * result + (title?.hashCode() ?: 0)
            result = 31 * result + inlineContent.hashCode()
            result = 31 * result + (width?.hashCode() ?: 0)
            result = 31 * result + (height?.hashCode() ?: 0)
            return result
        }

        override fun toString(): String {
            return "Image(" +
                "source='$source', " +
                "alt='$alt', " +
                "title=$title, " +
                "inlineContent=$inlineContent, " +
                "width=$width, " +
                "height=$height" +
                ")"
        }
    }

    /**
     * An inline hyperlink node holding a [destination] URL, an optional [title], and [inlineContent] for the link
     * label.
     */
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

    /** A soft line break, typically rendered as a space or ignored depending on the renderer. */
    public data object SoftLineBreak : InlineMarkdown

    /** An inline strong emphasis (bold) node, delimited by [delimiter] and containing [inlineContent]. */
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

    /** A plain text node holding a literal [content] string. */
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
