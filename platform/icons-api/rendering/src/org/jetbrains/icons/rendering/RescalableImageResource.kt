// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.icons.rendering

import org.jetbrains.icons.ExperimentalIconsApi
import org.jetbrains.icons.InternalIconsApi

@OptIn(ExperimentalIconsApi::class)
@InternalIconsApi
interface RescalableImageResource : ImageResource {
  fun scale(scale: ImageScale): BitmapImageResource
  fun calculateExpectedDimensions(scale: ImageScale): Bounds
}

@InternalIconsApi
sealed interface ImageScale {
  fun calculateScalingFactorByOriginalDimensions(width: Int, height: Int? = null): Float
}

@InternalIconsApi
class FixedScale(val scale: Float) : ImageScale {
  override fun calculateScalingFactorByOriginalDimensions(width: Int, height: Int?): Float = scale

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (javaClass != other?.javaClass) return false

    other as FixedScale

    return scale == other.scale
  }

  override fun hashCode(): Int {
    return scale.hashCode()
  }
}

@InternalIconsApi
class FitAreaScale(val width: Int, val height: Int) : ImageScale {
  override fun calculateScalingFactorByOriginalDimensions(width: Int, height: Int?): Float {
    val scale = this.width / width.toFloat()
    if (height != null) {
      return minOf(scale, this.height / height.toFloat())
    }
    return scale
  }

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (javaClass != other?.javaClass) return false

    other as FitAreaScale

    if (width != other.width) return false
    if (height != other.height) return false

    return true
  }

  override fun hashCode(): Int {
    var result = width
    result = 31 * result + height
    return result
  }
}
