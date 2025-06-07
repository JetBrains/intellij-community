package org.jetbrains.jewel.markdown.extensions.images

import coil3.ImageLoader
import coil3.PlatformContext

internal class Coil3ImageLoaderProvider {
    @Volatile private var instance: ImageLoader? = null

    fun get(context: PlatformContext): ImageLoader {
        // using the double-checked locking
        return instance
            ?: synchronized(this) { instance ?: ImageLoader.Builder(context).build().also { instance = it } }
    }
}
