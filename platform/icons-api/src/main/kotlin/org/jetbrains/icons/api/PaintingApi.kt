// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.icons.api

interface PaintingApi {
  val bounds: Bounds
  fun drawImage(image: BitmapImageResource, x: Int, y: Int, width: Int? = null, height: Int? = null)
  fun drawImage(image: RescalableImageResource, x: Int, y: Int, width: Int? = null, height: Int? = null)
}

class Bounds(
  val width: Int,
  val height: Int
) {
  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (javaClass != other?.javaClass) return false

    other as Bounds

    if (width != other.width) return false
    if (height != other.height) return false

    return true
  }

  override fun hashCode(): Int {
    var result = width
    result = 31 * result + height
    return result
  }

  override fun toString(): String {
    return "Bounds(width=$width, height=$height)"
  }

  fun canFit(other: Bounds): Boolean = other.width <= width && other.height <= height
}