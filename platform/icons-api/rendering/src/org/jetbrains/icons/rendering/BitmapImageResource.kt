// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.icons.rendering

import org.jetbrains.icons.InternalIconsApi

@InternalIconsApi
interface BitmapImageResource : ImageResource {
  fun getRGBPixels(): IntArray
  fun readPrefetchedPixel(pixels: IntArray, x: Int, y: Int): Int?
  fun getBandOffsetsToSRGB(): IntArray

  override val width: Int
  override val height: Int
}

@InternalIconsApi
object EmptyBitmapImageResource : BitmapImageResource {
  override fun getRGBPixels(): IntArray = intArrayOf()
  override fun readPrefetchedPixel(pixels: IntArray, x: Int, y: Int): Int = 0
  override fun getBandOffsetsToSRGB(): IntArray = intArrayOf(0, 1, 2, 3)

  override val width: Int = 0
  override val height: Int = 0
}
