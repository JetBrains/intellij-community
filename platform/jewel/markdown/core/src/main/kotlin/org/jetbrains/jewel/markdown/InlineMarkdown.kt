package org.jetbrains.jewel.markdown

import org.jetbrains.jewel.foundation.GenerateDataFunctions

/** A run of inline Markdown used as content for [block-level elements][MarkdownBlock]. */
public sealed interface InlineMarkdown {
    @GenerateDataFunctions public class Code(override val content: String) : InlineMarkdown, WithTextContent

    public interface CustomNode : InlineMarkdown {
        /**
         * If this custom node has a text-based representation, this function should return it. Otherwise, it should
         * return null.
         */
        public fun contentOrNull(): String? = null
    }

    @GenerateDataFunctions
    public class Emphasis(public val delimiter: String, override val inlineContent: List<InlineMarkdown>) :
        InlineMarkdown, WithInlineMarkdown {
        public constructor(
            delimiter: String,
            vararg inlineContent: InlineMarkdown,
        ) : this(delimiter, inlineContent.toList())
    }

    public data object HardLineBreak : InlineMarkdown

    @GenerateDataFunctions public class HtmlInline(override val content: String) : InlineMarkdown, WithTextContent

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
    }

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
    }

    public data object SoftLineBreak : InlineMarkdown

    @GenerateDataFunctions
    public class StrongEmphasis(public val delimiter: String, override val inlineContent: List<InlineMarkdown>) :
        InlineMarkdown, WithInlineMarkdown {
        public constructor(
            delimiter: String,
            vararg inlineContent: InlineMarkdown,
        ) : this(delimiter, inlineContent.toList())
    }

    @GenerateDataFunctions public class Text(override val content: String) : InlineMarkdown, WithTextContent
}
