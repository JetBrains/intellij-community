package org.jetbrains.jewel.markdown.rendering

import androidx.compose.ui.text.AnnotatedString
import org.jetbrains.jewel.foundation.ExperimentalJewelApi
import org.jetbrains.jewel.markdown.InlineMarkdown
import org.jetbrains.jewel.markdown.extensions.MarkdownProcessorExtension

@ExperimentalJewelApi
public interface InlineMarkdownRenderer {

    /**
     * Render the [inlineMarkdown] as an [AnnotatedString], using the [styling]
     * provided.
     */
    public fun renderAsAnnotatedString(inlineMarkdown: List<InlineMarkdown>, styling: InlinesStyling): AnnotatedString

    public companion object {

        /** Create a default inline renderer, with the [extensions] provided. */
        public fun default(extensions: List<MarkdownProcessorExtension> = emptyList()): InlineMarkdownRenderer =
            DefaultInlineMarkdownRenderer(extensions)
    }
}
