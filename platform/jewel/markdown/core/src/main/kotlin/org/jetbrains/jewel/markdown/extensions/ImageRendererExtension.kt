package org.jetbrains.jewel.markdown.extensions

import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.runtime.Composable
import org.jetbrains.jewel.foundation.ExperimentalJewelApi
import org.jetbrains.jewel.markdown.InlineMarkdown

/** An extension for the Jewel Markdown rendering engine. */
@ExperimentalJewelApi
public interface ImageRendererExtension {

    @Composable public fun imageContent(image: InlineMarkdown.Image): InlineTextContent
}
