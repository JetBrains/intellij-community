package org.jetbrains.jewel.markdown.extensions

import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.jewel.foundation.ExperimentalJewelApi
import org.jetbrains.jewel.markdown.InlineMarkdown

/**
 * Extension for rendering images in Jewel Markdown.
 *
 * Implement this interface to provide a custom renderer for images. This is useful for handling image loading from
 * different sources (e.g., network, local files) or for applying custom visual treatments to images.
 *
 * The [renderImage] function will be called for each image found in the Markdown content.
 */
@ApiStatus.Experimental
@ExperimentalJewelApi
public interface ImageRendererExtension {
    /**
     * Renders an image from a Markdown document.
     *
     * @param image The image data, containing information like the image URL and alt text.
     * @return An [InlineTextContent] that will be embedded in the text flow, which will be used to display the image,
     *   or `null` if the image could not be loaded.
     * @see renderImage
     */
    @Deprecated(
        message = "Use renderImage instead, which provides explicit loading/success/failed states",
        replaceWith = ReplaceWith("renderImage(image)"),
    )
    @Composable
    public fun renderImageContent(image: InlineMarkdown.Image): InlineTextContent? = null

    /**
     * Renders an image from a Markdown document.
     *
     * Override this function to provide custom image rendering with explicit state handling. The default implementation
     * delegates to the deprecated [renderImageContent] for backward compatibility.
     *
     * @param image The image data, containing information like the image URL and alt text.
     * @return An [ImageRenderResult] indicating the current state of the image: [ImageRenderResult.Loading] while the
     *   image is being fetched, [ImageRenderResult.Success] with the content when loaded, or [ImageRenderResult.Failed]
     *   if loading failed.
     */
    @Composable
    public fun renderImage(image: InlineMarkdown.Image): ImageRenderResult {
        @Suppress("DEPRECATION") val content = renderImageContent(image)
        return if (content != null) {
            ImageRenderResult.Success(content)
        } else {
            ImageRenderResult.Failed
        }
    }
}

/**
 * Represents the result of rendering an image in Markdown.
 *
 * This sealed class allows callers to distinguish between loading, success, and failure states, enabling appropriate UI
 * handling for each case (e.g., showing a loading indicator during loading, the image on success, or a fallback link on
 * failure).
 */
@ApiStatus.Experimental
@ExperimentalJewelApi
@Immutable
public sealed class ImageRenderResult {
    /**
     * The image is currently loading.
     *
     * @param content Optional inline content to display while loading (e.g., a loading indicator). If null, the
     *   placeholder text from the markdown will be shown.
     */
    public data class Loading(val content: InlineTextContent? = null) : ImageRenderResult()

    /** The image loaded successfully. */
    public data class Success(val content: InlineTextContent) : ImageRenderResult()

    /** The image failed to load. */
    public data object Failed : ImageRenderResult()
}
