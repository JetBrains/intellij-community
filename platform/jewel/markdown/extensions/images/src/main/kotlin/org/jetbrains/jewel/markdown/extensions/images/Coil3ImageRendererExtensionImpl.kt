package org.jetbrains.jewel.markdown.extensions.images

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.unit.sp
import coil3.ImageLoader
import coil3.compose.LocalPlatformContext
import coil3.compose.asPainter
import coil3.request.ErrorResult
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.size.Size
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.delay
import org.jetbrains.jewel.foundation.util.JewelLogger
import org.jetbrains.jewel.markdown.DimensionSize
import org.jetbrains.jewel.markdown.InlineMarkdown
import org.jetbrains.jewel.markdown.extensions.ImageRenderResult
import org.jetbrains.jewel.markdown.extensions.ImageRendererExtension
import org.jetbrains.jewel.markdown.rendering.LocalMarkdownImageSourceResolver
import org.jetbrains.jewel.ui.component.CircularProgressIndicator
import org.jetbrains.jewel.ui.component.styling.CircularProgressStyle

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
     * @return An [ImageRenderResult] representing the current loading state of the image.
     */
    @Composable
    public override fun renderImage(image: InlineMarkdown.Image): ImageRenderResult {
        val imageSourceResolver = LocalMarkdownImageSourceResolver.current
        val resolvedImageSource = remember(image.source) { imageSourceResolver.resolve(image.source) }

        // Known limitation (JEWEL-1364): the state remembered here (debouncedSource, and displayed/hasError below) is
        // keyed to this composable's call position, not to the specific image occurrence, so
        // inserting, removing, or reordering images can therefore briefly show a previous occurrence's image or
        // failure state in a reused slot before the new load settles.
        // Debounce source changes as to avoid trying to fetch images needlessly while the user is not done typing.
        var debouncedSource by remember { mutableStateOf(resolvedImageSource) }
        LaunchedEffect(resolvedImageSource) {
            if (debouncedSource != resolvedImageSource) {
                delay(SOURCE_DEBOUNCE)
                debouncedSource = resolvedImageSource
            }
        }

        // Only swap the current image displayed once a new load succeeds (stale-while-revalidate) so the switching
        // never blanks. If an image fails to load, then we show the hyperlink.
        val platformContext = LocalPlatformContext.current
        var displayed by remember { mutableStateOf<DisplayedImage?>(null) }
        var hasError by remember(debouncedSource) { mutableStateOf(false) }
        LaunchedEffect(debouncedSource, platformContext) {
            val request =
                ImageRequest.Builder(platformContext)
                    .data(debouncedSource)
                    // make sure the image isn't downscaled to the placeholder size
                    .size(Size.ORIGINAL)
                    .build()

            when (val result = imageLoader.execute(request)) {
                is SuccessResult -> {
                    hasError = false
                    displayed = DisplayedImage(result.image.asPainter(platformContext), result)
                }

                is ErrorResult -> {
                    hasError = true
                    displayed = null
                    JewelLogger.getInstance(this@Coil3ImageRendererExtensionImpl.javaClass)
                        .warn("Failed to load AsyncImage from $debouncedSource:", result.throwable)
                }
            }
        }

        if (hasError) return ImageRenderResult.Failed

        val current = displayed
        if (current == null) {
            // Reserve the specified size while loading so the layout doesn't jump when the image arrives
            val loadingPlaceholder =
                computePlaceholder(imageResult = null, specifiedWidth = image.width, specifiedHeight = image.height)
            val hasReservedSize = image.width != null || image.height != null
            val description = image.title?.takeIf { hasReservedSize } ?: LOADING_INDICATOR_DESCRIPTION
            return ImageRenderResult.Loading(createLoadingIndicator(loadingPlaceholder, description))
        }

        val placeholder =
            computePlaceholder(
                imageResult = current.result,
                specifiedWidth = image.width,
                specifiedHeight = image.height,
            )

        val content =
            InlineTextContent(placeholder) {
                Image(
                    painter = current.painter,
                    contentDescription = image.title,
                    modifier = Modifier.fillMaxSize(),
                    contentScale =
                        if (image.width != null && image.height != null) {
                            ContentScale.FillBounds
                        } else {
                            ContentScale.Fit
                        },
                )
            }
        return ImageRenderResult.Success(content)
    }

    private fun createLoadingIndicator(placeholder: Placeholder, description: String): InlineTextContent =
        InlineTextContent(placeholder) {
            Box(
                modifier = Modifier.fillMaxSize().semantics { contentDescription = description },
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator(style = DefaultLoadingIndicatorStyle)
            }
        }

    /**
     * Computes the placeholder size for the image.
     *
     * If both dimensions are specified, those are used. If only one dimension is specified, the other is scaled
     * proportionally based on the loaded image's aspect ratio. If no dimensions are specified, the loaded image's
     * original dimensions are used. While loading, a minimal placeholder is used unless dimensions are specified.
     */
    @Composable
    private fun computePlaceholder(
        imageResult: SuccessResult?,
        specifiedWidth: DimensionSize?,
        specifiedHeight: DimensionSize?,
    ): Placeholder {
        val density = LocalDensity.current

        // At least one dimension is unspecified or requires the image to compute
        if (imageResult == null) {
            // Known pixel dimensions can be reserved while loading; unspecified dimensions fall back to the minimal
            // placeholder.
            val pixelWidth = specifiedWidth?.toPixels()
            val pixelHeight = specifiedHeight?.toPixels()

            return with(density) {
                Placeholder(
                    width = pixelWidth?.toSp() ?: 1.sp,
                    height = pixelHeight?.toSp() ?: 1.sp,
                    placeholderVerticalAlign = PlaceholderVerticalAlign.Bottom,
                )
            }
        }

        // If we have a result, compute the final dimensions
        val loadedImage = imageResult.image
        val loadedWidth = loadedImage.width
        val loadedHeight = loadedImage.height

        // Resolve the specified dimensions to pixel values
        val resolvedWidth = specifiedWidth?.toPixels()
        val resolvedHeight = specifiedHeight?.toPixels()

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

    private fun DimensionSize.toPixels(): Int =
        when (this) {
            is DimensionSize.Pixels -> value
        }

    /** The currently displayed image: a stable painter over the loaded bytes plus its result (for sizing). */
    private data class DisplayedImage(val painter: Painter, val result: SuccessResult)

    internal companion object {
        const val LOADING_INDICATOR_DESCRIPTION = "Image loading indicator"
        val DefaultLoadingIndicatorStyle = CircularProgressStyle(frameTime = 125.milliseconds, color = Color.Gray)

        /** How long the resolved image source must stay unchanged before a load is triggered. */
        private val SOURCE_DEBOUNCE = 300.milliseconds
    }
}
