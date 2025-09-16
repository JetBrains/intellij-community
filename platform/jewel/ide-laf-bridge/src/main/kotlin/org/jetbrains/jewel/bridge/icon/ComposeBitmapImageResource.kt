// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jewel.bridge.icon

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asComposeImageBitmap
import org.jetbrains.icons.api.BitmapImageResource
import org.jetbrains.icons.api.Bounds
import org.jetbrains.icons.api.ImageResourceWithCrossApiCache
import org.jetbrains.icons.api.ImageScale
import org.jetbrains.icons.api.RescalableImageResource
import org.jetbrains.icons.api.cachedBitmap
import org.jetbrains.skia.Bitmap
import org.jetbrains.skia.ColorAlphaType
import org.jetbrains.skia.ImageInfo

public fun BitmapImageResource.composeBitmap(): ImageBitmap {
    // TODO Check for compose image bitmap
    val cache = (this as? ImageResourceWithCrossApiCache)?.crossApiCache
    return cache?.cachedBitmap<ImageBitmap> {
        composeBitmapWithoutCaching().second
    } ?: composeBitmapWithoutCaching().second
}

public fun RescalableImageResource.composeBitmap(scale: ImageScale): ImageBitmap {
    // TODO Check for compose image bitmap
    val cache = (this as? ImageResourceWithCrossApiCache)?.crossApiCache
    val cached = cache?.cachedBitmap<SingleBitmapCache> { SingleBitmapCache() }
    if (cached != null) {
        return cached.getOrPut(this, scale)
    } else {
        return scale(scale).composeBitmap()
    }
}

private class SingleBitmapCache {
    private var lastBitmap: CachedBitmap? = null
        
    private class CachedBitmap(
        val dimensions: Bounds,
        val composeBitmap: ImageBitmap,
        val bitmap: Bitmap
    )
    
    fun getOrPut(image: RescalableImageResource, scale: ImageScale): ImageBitmap {
        val last = lastBitmap
        val expectedDimensions = image.calculateExpectedDimensions(scale)
        if (last == null) {
            return createNewBitmap(image, scale, expectedDimensions)
        } else {
            if (last.dimensions == expectedDimensions) {
                return last.composeBitmap
            } else if (last.dimensions.canFit(expectedDimensions)) {
                last.bitmap.setPixelsFrom(image.scale(scale))
                return last.composeBitmap
            } else {
                return createNewBitmap(image, scale, expectedDimensions)
            }
        }
    }
    
    private fun createNewBitmap(image: RescalableImageResource, imageScale: ImageScale, dimensions: Bounds): ImageBitmap {
        val (skia, compose) = image.scale(imageScale).composeBitmapWithoutCaching()
        val new = CachedBitmap(
            dimensions,
            compose,
            skia
        )
        lastBitmap = new
        return new.composeBitmap
    }
}

private fun BitmapImageResource.composeBitmapWithoutCaching(): Pair<Bitmap, ImageBitmap> {
    val bitmap = Bitmap()
    bitmap.allocPixels(ImageInfo.makeS32(width, height, ColorAlphaType.UNPREMUL))
    bitmap.setPixelsFrom(this)
    return bitmap to bitmap.asComposeImageBitmap()
}

private fun Bitmap.setPixelsFrom(image: BitmapImageResource) {
    val bytesPerPixel = 4
    val pixels = ByteArray(width * height * bytesPerPixel)

    var k = 0
    for (y in 0 until height) {
        for (x in 0 until width) {
            val argb = image.getRGBOrNull(x, y) ?: 0
            val a = (argb shr 24) and 0xff
            val r = (argb shr 16) and 0xff
            val g = (argb shr 8) and 0xff
            val b = (argb shr 0) and 0xff
            pixels[k++] = b.toByte()
            pixels[k++] = g.toByte()
            pixels[k++] = r.toByte()
            pixels[k++] = a.toByte()
        }
    }
    
    installPixels(pixels)
}
