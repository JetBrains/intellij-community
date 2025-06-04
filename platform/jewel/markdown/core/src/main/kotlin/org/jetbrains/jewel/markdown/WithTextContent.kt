package org.jetbrains.jewel.markdown

import org.jetbrains.jewel.foundation.ExperimentalJewelApi

/** An inline Markdown node that has a plain text content, or can be rendered as plain text. */
@ExperimentalJewelApi
public interface WithTextContent {
    /** The plain text content or representation of this node. */
    public val content: String
}
