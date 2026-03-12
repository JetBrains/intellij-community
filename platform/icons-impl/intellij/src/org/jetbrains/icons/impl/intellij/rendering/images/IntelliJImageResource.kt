// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.icons.impl.intellij.rendering.images

import com.intellij.ui.scale.ScaleContext
import com.intellij.ui.scale.ScaleType
import com.intellij.util.JBHiDPIScaledImage
import org.jetbrains.icons.impl.rendering.CachedGPUImageResourceHolder
import org.jetbrains.icons.rendering.BitmapImageResource
import org.jetbrains.icons.rendering.Bounds
import org.jetbrains.icons.rendering.EmptyBitmapImageResource
import org.jetbrains.icons.rendering.ImageModifiers
import org.jetbrains.icons.rendering.ImageScale
import org.jetbrains.icons.rendering.RescalableImageResource

internal class IntelliJImageResource(
  private val holder: SwingImageResourceHolder,
  private val imageModifiers: ImageModifiers? = null
): RescalableImageResource, CachedGPUImageResourceHolder() {
  private val expectedDimensions by lazy { holder.getExpectedDimensions() }

  override fun scale(scale: ImageScale): BitmapImageResource {
    val objScale = scale.calculateScalingFactorByOriginalDimensions(width, height)
    val scaleContext = ScaleContext.of(arrayOf(
      ScaleType.OBJ_SCALE.of(objScale),
      ScaleType.USR_SCALE.of(1.0),
      ScaleType.SYS_SCALE.of(1.0),
    ))
    val rawImage = holder.getImage(scaleContext, imageModifiers) ?: return EmptyBitmapImageResource
    val awtImage = (rawImage as? JBHiDPIScaledImage)?.delegate ?: rawImage
    return AwtImageResource(awtImage)
  }

  override fun calculateExpectedDimensions(scale: ImageScale): Bounds {
    val ijScale = scale.calculateScalingFactorByOriginalDimensions(width, height)
    return Bounds(0, 0, width = (width * ijScale).toInt(), height = (height * ijScale).toInt())
  }

  override val width: Int by lazy { expectedDimensions.width }
  override val height: Int by lazy { expectedDimensions.height }
}
