package org.jetbrains.jewel.markdown.extensions.images

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.unit.sp
import coil3.ImageLoader
import coil3.compose.LocalPlatformContext
import coil3.compose.rememberAsyncImagePainter
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.size.Size
import kotlin.time.Duration.Companion.milliseconds
import org.jetbrains.jewel.foundation.util.JewelLogger
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
     * @param image The [InlineMarkdown.Image] data object containing the source, alt text, and title.
     * @return An [ImageRenderResult] representing the current loading state of the image.
     */
    @Composable
    public override fun renderImage(image: InlineMarkdown.Image): ImageRenderResult {
        val imageSourceResolver = LocalMarkdownImageSourceResolver.current
        val resolvedImageSource = remember(image.source) { imageSourceResolver.resolve(image.source) }
        var imageResult by remember(resolvedImageSource) { mutableStateOf<SuccessResult?>(null) }
        var hasError by remember(resolvedImageSource) { mutableStateOf(false) }

        // Track whether we've previously seen a failure to prevent flickering when typing
        // invalid URLs -- we keep showing Failed instead of Loading indicator until Coil
        // gives us a definitive result for the new source.
        var previouslyFailed by remember(resolvedImageSource) { mutableStateOf(false) }

        // Remember the ImageRequest model to prevent Coil from restarting loading unnecessarily
        // when the composition recomposes but the source doesn't change.
        val platformContext = LocalPlatformContext.current
        val model =
            remember(resolvedImageSource, platformContext) {
                ImageRequest.Builder(platformContext)
                    .data(resolvedImageSource)
                    // make sure image doesn't get downscaled to the placeholder size
                    .size(Size.ORIGINAL)
                    .build()
            }

        val painter =
            rememberAsyncImagePainter(
                model = model,
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
                    val message = "Failed to load AsyncImage from $resolvedImageSource:"
                    JewelLogger.getInstance(this.javaClass).warn(message, error.result.throwable)
                },
            )

        if (hasError) {
            previouslyFailed = true
            return ImageRenderResult.Failed
        }

        val result = imageResult
        if (result == null) {
            // Anti-flickering when typing: no loading indication for an image previously failed to load
            return if (previouslyFailed) {
                ImageRenderResult.Failed
            } else {
                ImageRenderResult.Loading(createLoadingIndicator())
            }
        }

        previouslyFailed = false

        val placeholder =
            with(LocalDensity.current) {
                val imageSize = result.image
                // `toSp` ensures that the placeholder size matches the original image size in pixels.
                // This approach doesn't allow images from appearing larger with different screen scaling,
                // but simply maintains behavior consistent with standalone AsyncImage rendering.
                Placeholder(
                    width = imageSize.width.toSp(),
                    height = imageSize.height.toSp(),
                    placeholderVerticalAlign = PlaceholderVerticalAlign.Bottom,
                )
            }

        val content =
            InlineTextContent(placeholder) {
                Image(painter = painter, contentDescription = image.title, modifier = Modifier.fillMaxSize())
            }
        return ImageRenderResult.Success(content)
    }

    private fun createLoadingIndicator(): InlineTextContent =
        InlineTextContent(
            Placeholder(width = 16.sp, height = 16.sp, placeholderVerticalAlign = PlaceholderVerticalAlign.Center)
        ) {
            Box(
                modifier = Modifier.fillMaxSize().semantics { contentDescription = LOADING_INDICATOR_DESCRIPTION },
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator(style = DefaultLoadingIndicatorStyle)
            }
        }

    internal companion object {
        const val LOADING_INDICATOR_DESCRIPTION = "Image loading indicator"
        val DefaultLoadingIndicatorStyle = CircularProgressStyle(frameTime = 125.milliseconds, color = Color.Gray)
    }
}
