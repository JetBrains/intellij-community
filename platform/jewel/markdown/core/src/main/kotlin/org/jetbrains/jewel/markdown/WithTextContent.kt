package org.jetbrains.jewel.markdown

import org.jetbrains.annotations.ApiStatus
import org.jetbrains.jewel.foundation.ExperimentalJewelApi

/** An inline Markdown node that has a plain text content, or can be rendered as plain text. */
@ApiStatus.Experimental
@ExperimentalJewelApi
public interface WithTextContent {
    /** The plain text content or representation of this node. */
    public val content: String
}
