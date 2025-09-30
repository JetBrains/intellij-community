package org.jetbrains.jewel.markdown.extensions.images

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.unit.sp
import coil3.ImageLoader
import coil3.compose.LocalPlatformContext
import coil3.compose.rememberAsyncImagePainter
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.size.Size
import org.jetbrains.jewel.foundation.util.JewelLogger
import org.jetbrains.jewel.markdown.InlineMarkdown
import org.jetbrains.jewel.markdown.extensions.ImageRendererExtension
import org.jetbrains.jewel.markdown.rendering.LocalMarkdownImageSourceResolver

/**
 * An [ImageRendererExtension] that uses Coil 3 to render images from Markdown.
 *
 * This implementation is designed to be robust, handling image loading asynchronously and resizing the layout correctly
 * upon success. It uses a callback-driven approach to update its state, which avoids common race conditions and
 * lifecycle issues in Compose.
 *
 * @param imageLoader The Coil [ImageLoader] instance used for all image requests.
 */
internal class Coil3ImageRendererExtensionImpl(private val imageLoader: ImageLoader) : ImageRendererExtension {
    /**
     * Composes the content for an inline Markdown image.
     *
     * This function creates an [InlineTextContent] which defines a placeholder in the text layout. The size of this
     * placeholder is initially small and is resized upon successful image loading to match the image's dimensions. The
     * actual image is then rendered inside this placeholder.
     *
     * @param image The [InlineMarkdown.Image] data object containing the source, alt text, and title.
     * @return An [InlineTextContent] that can be used by a `Text` or `BasicText` composable to render the image inline.
     */
    @Composable
    public override fun renderImageContent(image: InlineMarkdown.Image): InlineTextContent {
        val imageSourceResolver = LocalMarkdownImageSourceResolver.current
        val resolvedImageSource = remember(image.source) { imageSourceResolver.resolve(image.source) }
        var imageResult by remember(resolvedImageSource) { mutableStateOf<SuccessResult?>(null) }

        val painter =
            rememberAsyncImagePainter(
                model =
                    ImageRequest.Builder(LocalPlatformContext.current)
                        .data(resolvedImageSource)
                        // make sure image doesn't get downscaled to the placeholder size
                        .size(Size.ORIGINAL)
                        .build(),
                imageLoader = imageLoader,
                onSuccess = { successState ->
                    // onSuccess should only be called once, but adding additional protection from
                    // unnecessary rerender
                    if (imageResult == null) {
                        imageResult = successState.result
                    }
                },
                onError = { error ->
                    JewelLogger.getInstance(this.javaClass).warn("AsyncImage loading failed.", error.result.throwable)
                },
            )

        val placeholder =
            imageResult?.let {
                val imageSize = it.image
                with(LocalDensity.current) {
                    // `toSp` ensures that the placeholder size matches the original image size in pixels.
                    // This approach doesn't allow images from appearing larger with different screen scaling,
                    // but simply maintains behavior consistent with standalone AsyncImage rendering.
                    Placeholder(
                        width = imageSize.width.toSp(),
                        height = imageSize.height.toSp(),
                        placeholderVerticalAlign = PlaceholderVerticalAlign.Bottom,
                    )
                }
            }
                ?: run {
                    Placeholder(width = 0.sp, height = 1.sp, placeholderVerticalAlign = PlaceholderVerticalAlign.Bottom)
                }

        return InlineTextContent(placeholder) {
            Image(painter = painter, contentDescription = image.title, modifier = Modifier.fillMaxSize())
        }
    }
}
