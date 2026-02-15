package org.jetbrains.jewel.markdown

import org.jetbrains.annotations.ApiStatus
import org.jetbrains.jewel.foundation.ExperimentalJewelApi

/** An inline Markdown node that contains other [InlineMarkdown] nodes. */
@ApiStatus.Experimental
@ExperimentalJewelApi
public interface WithInlineMarkdown {
    /** Child inline Markdown nodes. */
    public val inlineContent: List<InlineMarkdown>
}
