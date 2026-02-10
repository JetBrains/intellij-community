// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.icons.rendering

import org.jetbrains.icons.ExperimentalIconsApi
import org.jetbrains.icons.InternalIconsApi
import org.jetbrains.icons.design.Color
import org.jetbrains.icons.filters.ColorFilter

/**
 * Abstraction of painting API, this is used to define icons or graphics that are customizable
 * but also reusable between different environments, where graphic api differ.
 */
@InternalIconsApi
interface PaintingApi {
  val bounds: Bounds
  val scaling: ScalingContext
  @OptIn(ExperimentalIconsApi::class)
  fun drawImage(
    image: ImageResource,
    x: Int = 0,
    y: Int = 0,
    width: Int? = null,
    height: Int? = null,
    srcX: Int = 0,
    srcY: Int = 0,
    srcWidth: Int? = null,
    srcHeight: Int? = null,
    alpha: Float = 1.0f,
    colorFilter: ColorFilter? = null
  )
  fun drawCircle(color: Color, x: Int, y: Int, radius: Float, alpha: Float = 1f, mode: DrawMode = DrawMode.Fill)
  fun drawRect(color: Color, x: Int, y: Int, width: Int, height: Int, alpha: Float = 1f, mode: DrawMode = DrawMode.Fill)
  fun getUsedBounds(): Bounds
  fun withCustomContext(bounds: Bounds, overrideColorFilter: ColorFilter? = null): PaintingApi
}

@InternalIconsApi
enum class DrawMode {
  Fill,
  Clear,
  Stroke
}

@InternalIconsApi
interface ScalingContext {
  val display: Float
}