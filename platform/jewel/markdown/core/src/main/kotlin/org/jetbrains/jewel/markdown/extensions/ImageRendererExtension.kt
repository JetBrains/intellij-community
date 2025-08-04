package org.jetbrains.jewel.markdown.extensions

import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.runtime.Composable
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.jewel.foundation.ExperimentalJewelApi
import org.jetbrains.jewel.markdown.InlineMarkdown

/**
 * Extension for rendering images in Jewel Markdown.
 *
 * Implement this interface to provide a custom renderer for images. This is useful for handling image loading from
 * different sources (e.g., network, local files) or for applying custom visual treatments to images.
 *
 * The [renderImageContent] function will be called for each image found in the Markdown content.
 */
@ApiStatus.Experimental
@ExperimentalJewelApi
public interface ImageRendererExtension {
    /**
     * Renders an image from a Markdown document.
     *
     * @param image The image data, containing information like the image URL and alt text.
     * @return An [InlineTextContent] that will be embedded in the text flow, which will be used to display the image.
     */
    @Composable public fun renderImageContent(image: InlineMarkdown.Image): InlineTextContent
}
