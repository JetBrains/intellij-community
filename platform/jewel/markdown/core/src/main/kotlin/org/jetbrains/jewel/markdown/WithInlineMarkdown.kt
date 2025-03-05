package org.jetbrains.jewel.markdown

/** An inline Markdown node that contains other [InlineMarkdown] nodes. */
public interface WithInlineMarkdown {
    /** Child inline Markdown nodes. */
    public val inlineContent: List<InlineMarkdown>
}
