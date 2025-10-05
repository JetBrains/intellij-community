package org.jetbrains.jewel.markdown.extensions.images

import androidx.compose.runtime.Composable
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.compose.LocalPlatformContext
import coil3.memory.MemoryCache
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.jewel.foundation.ExperimentalJewelApi
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
@ApiStatus.Experimental
@ExperimentalJewelApi
public class Coil3ImageRendererExtension(private val imageLoader: ImageLoader) : MarkdownRendererExtension {
    override val imageRendererExtension: ImageRendererExtension
        get() = Coil3ImageRendererExtensionImpl(imageLoader)

    public companion object {
        /**
         * A default image loader with a limited in-memory cache.
         *
         * This shouldn't be used if there is an app-wide image loader already available; instead, use the constructor
         * to pass in the already available image loader.
         *
         * Note that every invocation creates a new [ImageLoader]; it is not recommended to call this method multiple
         * times in a process. Instead, create one top-level instance and share it throughout the process if at all
         * possible.
         */
        @ApiStatus.Experimental
        @ExperimentalJewelApi
        @Composable
        public fun withDefaultLoader(): Coil3ImageRendererExtension = withDefaultLoader(LocalPlatformContext.current)

        /**
         * A default image loader with a limited in-memory cache.
         *
         * This shouldn't be used if there is an app-wide image loader already available; instead, use the constructor
         * to pass in the already available image loader.
         *
         * Note that every invocation creates a new [ImageLoader]; it is not recommended to call this method multiple
         * times in a process. Instead, create one top-level instance and share it throughout the process if at all
         * possible.
         *
         * @param context The [PlatformContext] to use to create the [ImageLoader].
         */
        @ApiStatus.Experimental
        @ExperimentalJewelApi
        public fun withDefaultLoader(context: PlatformContext): Coil3ImageRendererExtension =
            Coil3ImageRendererExtension(
                ImageLoader.Builder(context)
                    .memoryCache { MemoryCache.Builder().maxSizeBytes { DEFAULT_MEMORY_CACHE_SIZE }.build() }
                    .build()
            )
    }
}
