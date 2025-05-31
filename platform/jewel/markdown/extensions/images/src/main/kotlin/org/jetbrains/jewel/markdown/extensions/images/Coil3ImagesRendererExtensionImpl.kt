package org.jetbrains.jewel.markdown.extensions.images

import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import coil3.compose.LocalPlatformContext
import coil3.request.ImageRequest
import coil3.size.Size
import org.jetbrains.jewel.foundation.util.JewelLogger
import org.jetbrains.jewel.markdown.InlineMarkdown
import org.jetbrains.jewel.markdown.extensions.ImageRendererExtension
import org.jetbrains.jewel.markdown.extensions.MarkdownRendererExtension

/**
 * A [MarkdownRendererExtension] that supports rendering [InlineMarkdown.CustomDelimitedNode]s into
 * [androidx.compose.ui.text.AnnotatedString]s.
 */
public object Coil3ImagesRendererExtensionImpl : ImageRendererExtension {
    @Composable
    public override fun imageContent(image: InlineMarkdown.Image): InlineTextContent {

        val knownSize = remember(image.source) { mutableStateOf<ImageSize?>(null) }
        return InlineTextContent(
            with(LocalDensity.current) {
                // `toSp` ensures that the placeholder size matches the original image size in
                // pixels.
                // This approach doesn't allow images from appearing larger with different screen
                // scaling,
                // but simply maintains behavior consistent with standalone AsyncImage rendering.
                Placeholder(
                    width = knownSize.value?.width?.dp?.toSp() ?: 0.sp,
                    height = knownSize.value?.height?.dp?.toSp() ?: 1.sp,
                    placeholderVerticalAlign = PlaceholderVerticalAlign.Bottom,
                )
            }
        ) {
            AsyncImage(
                model =
                    ImageRequest.Builder(LocalPlatformContext.current)
                        .data(image.source)
                        // make sure image doesn't get downscaled to the placeholder size
                        .size(Size.ORIGINAL)
                        .build(),
                contentDescription = image.title,
                onSuccess = { state ->
                    // onSuccess should only be called once, but adding additional protection from
                    // unnecessary rerender
                    if (knownSize.value == null) {
                        knownSize.value = state.result.image.let { ImageSize(it.width, it.height) }
                    }
                },
                onError = { error ->
                    JewelLogger.getInstance("Jewel").warn("AsyncImage loading: ${error.result.throwable}")
                },
                modifier = knownSize.value?.let { Modifier.height(it.height.dp).width(it.width.dp) } ?: Modifier,
            )
        }
    }

    private data class ImageSize(val width: Int, val height: Int)
}
