// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.icons.api

import kotlin.reflect.KClass

interface ImageResourceProvider {
  companion object {
    @Volatile
    private var instance: ImageResourceProvider? = null

    @JvmStatic
    fun getInstance(): ImageResourceProvider = instance ?: DummyImageResourceProvider

    fun activate(provider: ImageResourceProvider) {
      instance = provider
    }
  }
}

object DummyImageResourceProvider : ImageResourceProvider

interface ImageResource {
  companion object
}

interface ImageResourceWithCrossApiCache {
  val crossApiCache: CrossApiImageBitmapCache
}

inline fun <reified TBitmap : Any> CrossApiImageBitmapCache.cachedBitmap(noinline generator: () -> TBitmap): TBitmap =
  cachedBitmap(TBitmap::class, generator)

interface CrossApiImageBitmapCache {
  fun <TBitmap : Any> cachedBitmap(bitmapClass: KClass<TBitmap>, generator: () -> TBitmap): TBitmap
}

interface BitmapImageResource : ImageResource {
  fun getRGB(x: Int, y: Int): Int
  val width: Int
  val height: Int

  companion object
}

interface RescalableImageResource : ImageResource {
  fun scale(scale: ImageScale): BitmapImageResource
}

sealed interface ImageScale {
  fun calculateScalingFactorByBounds(width: Int, height: Int? = null): Float
}

class FixedScale(val scale: Float) : ImageScale {
  override fun calculateScalingFactorByBounds(width: Int, height: Int?): Float = scale

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

class FitAreaScale(val width: Int, val height: Int) : ImageScale {
  override fun calculateScalingFactorByBounds(width: Int, height: Int?): Float {
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
