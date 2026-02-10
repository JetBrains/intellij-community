// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.icons.impl.rendering

import org.jetbrains.icons.rendering.BitmapImageResource
import org.jetbrains.icons.rendering.lowlevel.GPUImageResourceHolder
import java.awt.Image
import java.awt.Transparency
import java.awt.color.ColorSpace
import java.awt.image.BufferedImage
import java.awt.image.ComponentColorModel
import java.awt.image.DataBuffer
import java.awt.image.Raster

class AwtImageResource(
  val image: Image
) : CachedGPUImageResourceHolder(), BitmapImageResource {
  private val bufferedIamge by lazy {
    if (image is BufferedImage) return@lazy image
    val bi = BufferedImage(
      image.getWidth(null),
      image.getHeight(null),
      BufferedImage.TYPE_INT_ARGB
    )
    val g = bi.createGraphics()
    g.drawImage(image, 0, 0, null)
    g.dispose()
    return@lazy bi
  }

  override fun getRGBPixels(): IntArray {
    return bufferedIamge.getRGB(0, 0, bufferedIamge.raster.width, bufferedIamge.raster.height, null, 0, bufferedIamge.raster.width)
  }

  override fun readPrefetchedPixel(pixels: IntArray, x: Int, y: Int): Int? {
    return pixels.getOrNull(y * bufferedIamge.raster.width + x)
  }

  override fun getBandOffsetsToSRGB(): IntArray {
    return intArrayOf(0, 1, 2, 3)
  }

  override val width: Int = image.getWidth(null)
  override val height: Int = image.getHeight(null)
}

fun BitmapImageResource.awtImage(): Image {
  if (this is AwtImageResource) return image
  val cache = (this as? GPUImageResourceHolder)
  return cache?.getOrGenerateBitmap(Image::class) {
    awtImageWithoutCaching()
  } ?: awtImageWithoutCaching()
}

private fun BitmapImageResource.awtImageWithoutCaching(): Image {
  val pxs = getRGBPixels()
  val order = getBandOffsetsToSRGB()
  val raster = Raster.createInterleavedRaster(
    DirectDataBuffer(pxs),
    this.width,
    this.height,
    this.width * 4,
    4,
    order,
    null
  )
  val colorModel = ComponentColorModel(
    ColorSpace.getInstance(ColorSpace.CS_sRGB),
    true,
    false,
    Transparency.TRANSLUCENT,
    DataBuffer.TYPE_BYTE
  )
  return BufferedImage(colorModel, raster!!, false, null)
}

private class DirectDataBuffer(val pixels: IntArray) : DataBuffer(TYPE_BYTE, pixels.size) {
  override fun getElem(bank: Int, index: Int): Int {
    return pixels[index]
  }

  override fun setElem(bank: Int, index: Int, value: Int) {
    throw UnsupportedOperationException("no write access")
  }
}