package org.jetbrains.jewel.markdown

/** An inline Markdown node that has a plain text content, or can be rendered as plain text. */
public interface WithTextContent {
    /** The plain text content or representation of this node. */
    public val content: String
}
