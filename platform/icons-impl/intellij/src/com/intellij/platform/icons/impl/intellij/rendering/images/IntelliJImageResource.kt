// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.icons.impl.intellij.rendering.images

import com.intellij.ui.scale.JBUIScale
import com.intellij.ui.scale.ScaleContext
import com.intellij.ui.scale.ScaleType
import com.intellij.util.JBHiDPIScaledImage
import com.intellij.platform.icons.impl.rendering.CachedGPUImageResourceHolder
import com.intellij.platform.icons.rendering.BitmapImageResource
import com.intellij.platform.icons.rendering.Bounds
import com.intellij.platform.icons.rendering.EmptyBitmapImageResource
import com.intellij.platform.icons.rendering.ImageModifiers
import com.intellij.platform.icons.rendering.RescalableImageResource
import com.intellij.platform.icons.rendering.ThemeContext
import com.intellij.platform.icons.scale.IconScale

internal class IntelliJImageResource(
  private val holder: SwingImageResourceHolder,
  private val imageModifiers: ImageModifiers? = null,
  /** The theme whose inputs the image takes. [holder] reads them on each [scale]. */
  private val theme: ThemeContext,
): RescalableImageResource, CachedGPUImageResourceHolder() {
  private val expectedDimensions by lazy { holder.getExpectedDimensions() }

  @Suppress("TestOnlyProblems")
  override fun scale(density: Float, scale: IconScale): BitmapImageResource {
    val factor = scale.toFactor(density, width, height)
    // Split the total factor so the holder sees the logical size the image is painted at (density and IDE zoom apart from
    // the object scale), which selects `@WxH` size variants. The loaders rasterize at OBJ × USR × SYS, so the device
    // output is unchanged.
    val sysScale = if (density > 0f) density.toDouble() else 1.0
    val usrScale = JBUIScale.scale(1f).toDouble()
    val scaleContext = ScaleContext.of(arrayOf(
      ScaleType.OBJ_SCALE.of(factor / (sysScale * usrScale)),
      ScaleType.USR_SCALE.of(usrScale),
      ScaleType.SYS_SCALE.of(sysScale),
    ))
    val rawImage = holder.getImage(scaleContext, imageModifiers, theme) ?: return EmptyBitmapImageResource
    val awtImage = (rawImage as? JBHiDPIScaledImage)?.delegate ?: rawImage
    return AwtImageResource(awtImage)
  }

  override fun calculateExpectedDimensions(density: Float, scale: IconScale): Bounds {
    val ijScale = scale.toFactor(density, width, height)
    return Bounds(0, 0, width = (width * ijScale).toInt(), height = (height * ijScale).toInt())
  }

  override val width: Int by lazy { expectedDimensions.width }
  override val height: Int by lazy { expectedDimensions.height }
}
