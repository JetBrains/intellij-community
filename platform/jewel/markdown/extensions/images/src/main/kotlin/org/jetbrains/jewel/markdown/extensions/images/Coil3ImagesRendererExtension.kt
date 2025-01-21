package org.jetbrains.jewel.markdown.extensions.images

import androidx.compose.runtime.Composable
import coil3.ImageLoader
import coil3.compose.LocalPlatformContext
import org.jetbrains.jewel.markdown.extensions.ImageRendererExtension
import org.jetbrains.jewel.markdown.extensions.MarkdownRendererExtension

/** A [MarkdownRendererExtension] that supports rendering images and allows setting a custom image loader. */
public class Coil3ImagesRendererExtension(private val imageLoader: ImageLoader) : MarkdownRendererExtension {
    override val imageRendererExtension: ImageRendererExtension
        get() = Coil3ImagesRendererExtensionImpl(imageLoader)

    public companion object {
        @Composable
        public fun withDefaultLoader(): Coil3ImagesRendererExtension =
            Coil3ImagesRendererExtension(Coil3ImageLoaderProvider().get(LocalPlatformContext.current))
    }
}
