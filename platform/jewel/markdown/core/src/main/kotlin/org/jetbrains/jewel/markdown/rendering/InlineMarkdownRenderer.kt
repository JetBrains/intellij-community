package org.jetbrains.jewel.markdown.rendering

import androidx.compose.ui.text.AnnotatedString
import org.jetbrains.jewel.foundation.ExperimentalJewelApi
import org.jetbrains.jewel.markdown.InlineMarkdown

@ExperimentalJewelApi
public interface InlineMarkdownRenderer {
    /** Render the [inlineMarkdown] as an [AnnotatedString], using the [styling] provided. */
    public fun renderAsAnnotatedString(
        inlineMarkdown: Iterable<InlineMarkdown>,
        styling: InlinesStyling,
        enabled: Boolean,
        onUrlClicked: ((String) -> Unit)? = null,
    ): AnnotatedString
}
