package org.jetbrains.jewel.markdown.extensions.images

import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.sp
import coil3.ImageLoader
import coil3.compose.AsyncImage
import coil3.compose.LocalPlatformContext
import coil3.request.ImageRequest
import coil3.size.Size
import org.jetbrains.jewel.foundation.util.JewelLogger
import org.jetbrains.jewel.markdown.InlineMarkdown
import org.jetbrains.jewel.markdown.extensions.ImageRendererExtension
import org.jetbrains.jewel.ui.component.Text
import org.jetbrains.jewel.ui.component.Tooltip

/**
 * Renders Markdown images using Coil3.
 *
 * This implementation uses [AsyncImage] to load and display images. It reserves space for the image with a
 * [Placeholder] and updates its size upon successful loading to prevent layout shifts.
 *
 * @param imageLoader A custom [ImageLoader] for Coil3 image requests.
 */
internal class Coil3ImageRendererExtensionImpl(private val imageLoader: ImageLoader) : ImageRendererExtension {
    @Composable
    override fun renderImageContent(image: InlineMarkdown.Image): InlineTextContent {
        var knownSize by remember(image.source) { mutableStateOf<DpSize?>(null) }

        return InlineTextContent(
            with(LocalDensity.current) {
                Placeholder(
                    width = knownSize?.width?.toSp() ?: 0.sp,
                    height = knownSize?.height?.toSp() ?: 1.sp,
                    placeholderVerticalAlign = PlaceholderVerticalAlign.Bottom,
                )
            }
        ) {
            val density = LocalDensity.current
            Tooltip({ Text(image.alt) }, enabled = image.alt.isNotBlank()) {
                AsyncImage(
                    model =
                        ImageRequest.Builder(LocalPlatformContext.current)
                            .data(image.source)
                            // make sure the image doesn't get downscaled to the placeholder size
                            .size(Size.ORIGINAL)
                            .build(),
                    imageLoader = imageLoader,
                    contentDescription = image.title,
                    onSuccess = { state ->
                        // onSuccess should only be called once, but adding additional protection from
                        // unnecessary rerender
                        val newSize =
                            with(density) { state.result.image.let { DpSize(it.width.toDp(), it.height.toDp()) } }
                        if (knownSize != newSize) {
                            @Suppress("AssignedValueIsNeverRead") // False positive
                            knownSize = newSize
                        }
                    },
                    onError = { error ->
                        JewelLogger.getInstance("Markdown").warn("AsyncImage loading: ${error.result.throwable}")
                    },
                    modifier =
                        (knownSize?.let { size -> Modifier.size(size) } // If we have a size, let's use it
                            ?: Modifier) // Otherwise, we'll do it when we have it
                            .pointerHoverIcon(PointerIcon.Default),
                )
            }
        }
    }
}
