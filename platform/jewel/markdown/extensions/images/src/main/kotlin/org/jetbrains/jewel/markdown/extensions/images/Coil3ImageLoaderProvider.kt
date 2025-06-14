package org.jetbrains.jewel.markdown.extensions.images

import coil3.ImageLoader
import coil3.PlatformContext
import coil3.memory.MemoryCache

private const val MEMORY_CACHE_SIZE: Long = 20 * 1024 * 1024 // 20 MB

internal class Coil3ImageLoaderProvider {
    @Volatile private var instance: ImageLoader? = null

    fun get(context: PlatformContext): ImageLoader {
        // using the double-checked locking
        return instance
            ?: synchronized(this) {
                instance
                    ?: ImageLoader.Builder(context)
                        .memoryCache { MemoryCache.Builder().maxSizeBytes { MEMORY_CACHE_SIZE }.build() }
                        .build()
                        .also { instance = it }
            }
    }
}
