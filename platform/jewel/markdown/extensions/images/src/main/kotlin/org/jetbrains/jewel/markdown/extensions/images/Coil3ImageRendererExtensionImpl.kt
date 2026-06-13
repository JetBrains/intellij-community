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
import org.jetbrains.jewel.markdown.DimensionSize
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
     * If the image has a specified width and/or height, those dimensions are used for the placeholder. When only one
     * dimension is specified, the other is scaled proportionally based on the loaded image's aspect ratio.
     *
     * @param image The [InlineMarkdown.Image] data object containing the source, alt text, title, and optional
     *   dimensions.
     * @return An [InlineTextContent] that can be used by a `Text` or `BasicText` composable to render the image inline.
     */
    @Composable
    public override fun renderImageContent(image: InlineMarkdown.Image): InlineTextContent? {
        val imageSourceResolver = LocalMarkdownImageSourceResolver.current
        val resolvedImageSource = remember(image.source) { imageSourceResolver.resolve(image.source) }
        var imageResult by remember(resolvedImageSource) { mutableStateOf<SuccessResult?>(null) }
        var hasError by remember { mutableStateOf(false) }

        val painter =
            rememberAsyncImagePainter(
                model =
                    ImageRequest.Builder(LocalPlatformContext.current)
                        .data(resolvedImageSource)
                        // make sure image doesn't get downscaled to the placeholder size
                        .size(Size.ORIGINAL)
                        .build(),
                imageLoader = imageLoader,
                onLoading = { hasError = false },
                onSuccess = { successState ->
                    hasError = false
                    // onSuccess should only be called once, but adding additional protection from
                    // unnecessary rerender
                    if (imageResult == null) {
                        imageResult = successState.result
                    }
                },
                onError = { error ->
                    hasError = true
                    JewelLogger.getInstance(this.javaClass).warn("AsyncImage loading failed.", error.result.throwable)
                },
            )

        if (hasError) {
            return null
        }

        val placeholder =
            computePlaceholder(imageResult = imageResult, specifiedWidth = image.width, specifiedHeight = image.height)

        return InlineTextContent(placeholder) {
            Image(painter = painter, contentDescription = image.title, modifier = Modifier.fillMaxSize())
        }
    }

    /**
     * Computes the placeholder size for the image.
     *
     * If both dimensions are specified, those are used. If only one dimension is specified, the other is scaled
     * proportionally based on the loaded image's aspect ratio. If no dimensions are specified, the loaded image's
     * original dimensions are used. While loading, a minimal placeholder is used unless dimensions are specified.
     *
     * For percentage values, they are treated as a percentage of the original image size.
     */
    @Composable
    private fun computePlaceholder(
        imageResult: SuccessResult?,
        specifiedWidth: DimensionSize?,
        specifiedHeight: DimensionSize?,
    ): Placeholder {
        val density = LocalDensity.current

        // At least one dimension is unspecified or requires the image to compute; return the "empty" placeholder
        if (imageResult == null) {
            // For pixel values, we can show them immediately; for percentage we need the image
            val pixelWidth = (specifiedWidth as? DimensionSize.Pixels)?.value
            val pixelHeight = (specifiedHeight as? DimensionSize.Pixels)?.value

            if (pixelWidth != null && pixelHeight != null) {
                return with(density) {
                    Placeholder(
                        width = pixelWidth.toSp(),
                        height = pixelHeight.toSp(),
                        placeholderVerticalAlign = PlaceholderVerticalAlign.Bottom,
                    )
                }
            }

            // `toSp` ensures that the placeholder size matches the original image size in pixels.
            // This approach doesn't allow images from appearing larger with different screen scaling,
            // but simply maintains behavior consistent with standalone AsyncImage rendering.
            return Placeholder(width = 0.sp, height = 1.sp, placeholderVerticalAlign = PlaceholderVerticalAlign.Bottom)
        }

        // If we have a result, compute the final dimensions
        val loadedImage = imageResult.image
        val loadedWidth = loadedImage.width
        val loadedHeight = loadedImage.height

        // Resolve the specified dimensions to pixel values
        val resolvedWidth = specifiedWidth?.toPixels(loadedWidth)
        val resolvedHeight = specifiedHeight?.toPixels(loadedHeight)

        val (finalWidth, finalHeight) =
            when {
                resolvedWidth != null && resolvedHeight != null -> {
                    resolvedWidth to resolvedHeight
                }
                resolvedWidth != null -> {
                    // Scale height proportionally
                    val scaledHeight = (resolvedWidth.toFloat() / loadedWidth * loadedHeight).toInt()
                    resolvedWidth to scaledHeight
                }
                resolvedHeight != null -> {
                    // Scale width proportionally
                    val scaledWidth = (resolvedHeight.toFloat() / loadedHeight * loadedWidth).toInt()
                    scaledWidth to resolvedHeight
                }
                else -> {
                    // No dimensions specified, use original
                    loadedWidth to loadedHeight
                }
            }

        return with(density) {
            Placeholder(
                width = finalWidth.toSp(),
                height = finalHeight.toSp(),
                placeholderVerticalAlign = PlaceholderVerticalAlign.Bottom,
            )
        }
    }

    /**
     * Converts an [DimensionSize] to pixels. For [DimensionSize.Pixels], returns the value directly. For
     * [DimensionSize.Percent], returns the percentage of the [originalDimension].
     */
    private fun DimensionSize.toPixels(originalDimension: Int): Int =
        when (this) {
            is DimensionSize.Pixels -> value
            is DimensionSize.Percent -> (originalDimension * value / 100)
        }
}
