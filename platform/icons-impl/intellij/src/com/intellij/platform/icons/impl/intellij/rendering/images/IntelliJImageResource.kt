// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.icons.impl.intellij.rendering.images

import com.intellij.platform.icons.impl.rendering.CachedGPUImageResourceHolder
import com.intellij.platform.icons.impl.rendering.PaintedSize
import com.intellij.platform.icons.rendering.BitmapImageResource
import com.intellij.platform.icons.rendering.Bounds
import com.intellij.platform.icons.rendering.EmptyBitmapImageResource
import com.intellij.platform.icons.rendering.ImageModifiers
import com.intellij.platform.icons.rendering.RescalableImageResource
import com.intellij.platform.icons.rendering.ThemeContext
import com.intellij.platform.icons.scale.IconScale
import com.intellij.ui.JreHiDpiUtil
import com.intellij.ui.scale.JBUIScale
import com.intellij.ui.scale.ScaleContext
import com.intellij.ui.scale.ScaleType
import com.intellij.util.JBHiDPIScaledImage
import java.awt.Component

internal class IntelliJImageResource(
  private val holder: SwingImageResourceHolder,
  private val imageModifiers: ImageModifiers? = null,
  /** The theme whose inputs the image takes. [holder] reads them on each [scale]. */
  private val theme: ThemeContext,
): RescalableImageResource, CachedGPUImageResourceHolder() {
  private val expectedDimensions by lazy { holder.getExpectedDimensions(imageModifiers) }

  override fun scale(density: Float, scale: IconScale): BitmapImageResource = scale(density, scale, component = null)

  /**
   * [scale] for painting in [component], see [SwingImageResourceHolder.getImage]. [painted] is the exact logical size the
   * image is painted at, if the caller knows it. Without it, the size comes from the device pixels of [scale]. [zoom] is
   * the IDE zoom in the painted size, the user scale of the image.
   */
  @Suppress("TestOnlyProblems")
  fun scale(
    density: Float,
    scale: IconScale,
    component: Component?,
    painted: PaintedSize? = null,
    zoom: Float = JBUIScale.scale(1f),
  ): BitmapImageResource {
    val factor = scale.toFactor(density, width, height)
    val sysScale = if (density > 0f) density.toDouble() else 1.0
    val usrScale = zoom.toDouble()
    // The loaders rasterize at PIX_SCALE = DEV_SCALE * USR_SCALE * OBJ_SCALE. DEV_SCALE is SYS_SCALE only with the JRE
    // HiDPI mode, and else 1. The split keeps PIX_SCALE equal to the factor in both modes.
    val devScale = if (JreHiDpiUtil.isJreHiDPIEnabled()) sysScale else 1.0
    val scaleContext = ScaleContext.of(arrayOf(
      ScaleType.OBJ_SCALE.of(factor / (devScale * usrScale)),
      ScaleType.USR_SCALE.of(usrScale),
      ScaleType.SYS_SCALE.of(devScale),
    ))
    val size = painted ?: PaintedSize(width * factor / sysScale, height * factor / sysScale)
    val rawImage = holder.getImage(scaleContext, size, imageModifiers, theme, component) ?: return EmptyBitmapImageResource
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
