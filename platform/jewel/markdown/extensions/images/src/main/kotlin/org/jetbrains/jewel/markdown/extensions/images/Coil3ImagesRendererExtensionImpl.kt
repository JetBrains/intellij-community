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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.ImageLoader
import coil3.compose.AsyncImage
import coil3.compose.LocalPlatformContext
import coil3.compose.rememberAsyncImagePainter
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.size.Size
import coil3.toUri
import org.jetbrains.annotations.VisibleForTesting
import org.jetbrains.jewel.foundation.util.JewelLogger
import org.jetbrains.jewel.markdown.InlineMarkdown
import org.jetbrains.jewel.markdown.extensions.ImageRendererExtension

/**
 * Renders Markdown images using Coil3.
 *
 * This implementation uses [AsyncImage] to load and display images. It reserves space for the image with a
 * [Placeholder] and updates its size upon successful loading to prevent layout shifts.
 *
 * @param imageLoader A custom [ImageLoader] for Coil3 image requests.
 */
public class Coil3ImagesRendererExtensionImpl(private val imageLoader: ImageLoader) : ImageRendererExtension {
    @Composable
    public override fun renderImagesContent(image: InlineMarkdown.Image): InlineTextContent {
        val resolvedImageSource = remember(image.source) { resolveImageSource(image.source) }
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
                    JewelLogger.getInstance("Jewel").warn("AsyncImage error: ${error.result.throwable}")
                },
            )

        val placeholder =
            if (imageResult != null) {
                val imageSize = imageResult!!.image
                with(LocalDensity.current) {
                    // `toSp` ensures that the placeholder size matches the original image size in pixels.
                    // This approach doesn't allow images from appearing larger with different screen scaling,
                    // but simply maintains behavior consistent with standalone AsyncImage rendering.
                    Placeholder(
                        width = imageSize.width.dp.toSp(),
                        height = imageSize.height.dp.toSp(),
                        placeholderVerticalAlign = PlaceholderVerticalAlign.Bottom,
                    )
                }
            } else {
                Placeholder(width = 0.sp, height = 1.sp, placeholderVerticalAlign = PlaceholderVerticalAlign.Bottom)
            }

        return InlineTextContent(placeholder) {
            Image(painter = painter, contentDescription = image.title, modifier = Modifier.fillMaxSize())
        }
    }

    @VisibleForTesting
    internal fun resolveImageSource(rawDestination: String): String {
        val uri = rawDestination.toUri()

        if (uri.scheme != null) return rawDestination

        val resourceUrl = this@Coil3ImagesRendererExtensionImpl::class.java.classLoader.getResource(rawDestination)

        if (resourceUrl == null) {
            JewelLogger.getInstance("Jewel")
                .warn(
                    "Markdown image '$rawDestination' expected at classpath '$rawDestination' but not found. " +
                        "Please ensure it's in your 'src/main/resources/' folder."
                )
            return rawDestination // This will cause Coil to fail and not render anything.
        }

        return resourceUrl.toExternalForm()
    }
}
