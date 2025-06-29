package org.jetbrains.jewel.markdown.extensions.images

import androidx.compose.runtime.Composable
import coil3.ImageLoader
import coil3.compose.LocalPlatformContext
import coil3.memory.MemoryCache
import org.jetbrains.jewel.markdown.extensions.ImageRendererExtension
import org.jetbrains.jewel.markdown.extensions.MarkdownRendererExtension

private const val DEFAULT_MEMORY_CACHE_SIZE: Long = 20 * 1024 * 1024 // 20 MB

/**
 * A [MarkdownRendererExtension] for rendering images using the Coil3 library.
 *
 * This extension enables the display of images specified in Markdown, for example: `![An
 * image](https://example.com/image.png)`.
 *
 * It requires an [ImageLoader] to handle fetching and displaying images. For optimal performance and resource
 * management (e.g., shared memory and disk caches), it is recommended to provide a single, app-wide [ImageLoader]
 * instance via the constructor.
 */
public class Coil3ImagesRendererExtension(private val imageLoader: ImageLoader) : MarkdownRendererExtension {
    override val imageRendererExtension: ImageRendererExtension
        get() = Coil3ImagesRendererExtensionImpl(imageLoader)

    public companion object {
        /**
         * A default images loader with limited in-memory cache is to decouple clients from depending directly on coil
         * internals. This shouldn't be used if there is an app-wide image loader already availble; Which could be set
         * via a constructor.
         */
        @Composable
        public fun withDefaultLoader(): Coil3ImagesRendererExtension =
            Coil3ImagesRendererExtension(
                ImageLoader.Builder(LocalPlatformContext.current)
                    .memoryCache { MemoryCache.Builder().maxSizeBytes { DEFAULT_MEMORY_CACHE_SIZE }.build() }
                    .build()
            )
    }
}
