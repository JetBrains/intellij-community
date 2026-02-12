// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jewel.ui.icon

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asComposeImageBitmap
import androidx.compose.ui.graphics.asSkiaBitmap
import org.jetbrains.icons.rendering.BitmapImageResource
import org.jetbrains.icons.rendering.Bounds
import org.jetbrains.icons.rendering.lowlevel.GPUImageResourceHolder
import org.jetbrains.icons.rendering.ImageScale
import org.jetbrains.icons.rendering.RescalableImageResource
import org.jetbrains.skia.Bitmap
import org.jetbrains.skia.ColorAlphaType
import org.jetbrains.skia.ImageInfo
import org.jetbrains.icons.impl.rendering.CachedGPUImageResourceHolder
import org.jetbrains.skia.ColorType
import org.jetbrains.skia.impl.BufferUtil

internal class ComposeBitmapImageResource(
    public val imageBitmap: ImageBitmap
): BitmapImageResource, CachedGPUImageResourceHolder() {
    override fun getRGBPixels(): IntArray {
        val skia = imageBitmap.asSkiaBitmap()
        val pixelsNativePointer = skia.peekPixels()!!.addr
        val pixelsBuffer = BufferUtil.getByteBufferFromPointer(pixelsNativePointer, skia.rowBytes * skia.height)
        return pixelsBuffer.asIntBuffer().array()
    }

    override fun readPrefetchedPixel(pixels: IntArray, x: Int, y: Int): Int? {
        return pixels.getOrNull(y * imageBitmap.width + x)
    }

    override fun getBandOffsetsToSRGB(): IntArray {
        val skia = imageBitmap.asSkiaBitmap()
        return when (skia.colorInfo.colorType) {
            ColorType.RGB_888X -> intArrayOf(0, 1, 2, 3)
            ColorType.BGRA_8888 -> intArrayOf(2, 1, 0, 3)
            else -> throw UnsupportedOperationException("unsupported color type ${skia.colorInfo.colorType}")
        }
    }

    override val width: Int = imageBitmap.width
    override val height: Int = imageBitmap.height
}

internal fun BitmapImageResource.composeBitmap(): ImageBitmap {
    if (this is ComposeBitmapImageResource) return imageBitmap
    val cache = (this as? GPUImageResourceHolder)
    return cache?.getOrGenerateBitmap(ImageBitmap::class) {
        composeBitmapWithoutCaching().second
    } ?: composeBitmapWithoutCaching().second
}

internal fun RescalableImageResource.composeBitmap(scale: ImageScale): ImageBitmap {
    val cache = (this as? GPUImageResourceHolder)
    val cached = cache?.getOrGenerateBitmap(SingleBitmapCache::class) { SingleBitmapCache() }
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
    val prefetchedPixels = image.getRGBPixels()

    var k = 0
    for (y in 0 until height) {
        for (x in 0 until width) {
            val argb = image.readPrefetchedPixel(prefetchedPixels, x, y) ?: 0
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
