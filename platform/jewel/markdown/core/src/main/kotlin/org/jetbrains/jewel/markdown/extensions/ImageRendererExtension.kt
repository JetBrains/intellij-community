package org.jetbrains.jewel.markdown.extensions

import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.runtime.Composable
import org.jetbrains.jewel.foundation.ExperimentalJewelApi
import org.jetbrains.jewel.markdown.InlineMarkdown

/** An extension for the Jewel images rendering engine. */
@ExperimentalJewelApi
public interface ImageRendererExtension {
    @Composable public fun renderImagesContent(image: InlineMarkdown.Image): InlineTextContent
}
