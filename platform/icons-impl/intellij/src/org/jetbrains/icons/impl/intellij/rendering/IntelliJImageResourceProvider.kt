// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.icons.impl.intellij.rendering

import com.intellij.openapi.util.IconLoader
import com.intellij.openapi.util.IconLoader.filterIcon
import com.intellij.ui.icons.CachedImageIcon
import com.intellij.ui.icons.RgbImageFilterSupplier
import com.intellij.ui.scale.ScaleContext
import com.intellij.ui.scale.ScaleType
import com.intellij.util.JBHiDPIScaledImage
import org.jetbrains.icons.rendering.BitmapImageResource
import org.jetbrains.icons.rendering.Bounds
import org.jetbrains.icons.rendering.EmptyBitmapImageResource
import org.jetbrains.icons.rendering.ImageModifiers
import org.jetbrains.icons.rendering.ImageResource
import org.jetbrains.icons.rendering.ImageResourceLoader
import org.jetbrains.icons.rendering.ImageScale
import org.jetbrains.icons.rendering.RescalableImageResource
import org.jetbrains.icons.impl.rendering.AwtImageResource
import org.jetbrains.icons.impl.rendering.CachedGPUImageResourceHolder
import org.jetbrains.icons.impl.rendering.DefaultImageResourceProvider
import java.awt.Image
import java.awt.image.RGBImageFilter
import javax.swing.Icon

class IntelliJImageResourceProvider: DefaultImageResourceProvider() {
  override fun loadImage(loader: ImageResourceLoader, imageModifiers: ImageModifiers?): ImageResource {
    if (loader is SwingImageResourceLoader) {
      return fromSwingLoader(loader, imageModifiers)
    } else error("Unsupported loader: $loader")
  }

  override fun fromSwingIcon(icon: Icon, imageModifiers: ImageModifiers?): ImageResource {
    return IntelliJImageResource(LegacyIconImageResourceLoader(icon), imageModifiers)
  }

  private fun fromSwingLoader(loader: SwingImageResourceLoader, imageModifiers: ImageModifiers? = null): ImageResource =
    IntelliJImageResource(loader, imageModifiers)
}

private class LegacyIconImageResourceLoader(
  private val backingLegacyIcon: Icon
): BaseIntelliJImageResourceLoader() {
  override fun getExpectedDimensions(): Pair<Int, Int> {
    return backingLegacyIcon.iconWidth to backingLegacyIcon.iconHeight
  }

  override fun loadImage(scale: ScaleContext, imageModifiers: ImageModifiers?): Image? {
    val params = generateLoadIconParameters(imageModifiers)
    val svgPatcher = params.colorPatcher
    val icon = if (backingLegacyIcon is CachedImageIcon && svgPatcher != null) {
      backingLegacyIcon.createWithPatcher(svgPatcher, isDark = params.isDark, useStroke = params.isStroke)
    } else backingLegacyIcon
    val filtered = if (params.filters.isNotEmpty()) {
      filterIcon(icon = icon, filterSupplier = object : RgbImageFilterSupplier {
        override fun getFilter() = params.filters.first() as RGBImageFilter
      })
    } else icon
    return IconLoader.toImage(filtered, scale)
  }
}

internal class IntelliJImageResource(
  private val loader: SwingImageResourceLoader,
  private val imageModifiers: ImageModifiers? = null
): RescalableImageResource, CachedGPUImageResourceHolder() {

  override fun scale(scale: ImageScale): BitmapImageResource {
    val (width, height) = loader.getExpectedDimensions()
    val objScale = scale.calculateScalingFactorByOriginalDimensions(width, height)
    val scaleContext = ScaleContext.of(arrayOf(
      ScaleType.OBJ_SCALE.of(objScale),
      ScaleType.USR_SCALE.of(1.0),
      ScaleType.SYS_SCALE.of(1.0),
    ))
    val rawImage = loader.loadImage(scaleContext, imageModifiers) ?: return EmptyBitmapImageResource
    val awtImage = (rawImage as? JBHiDPIScaledImage)?.delegate ?: rawImage
    return AwtImageResource(awtImage)
  }

  override fun calculateExpectedDimensions(scale: ImageScale): Bounds {
    val (width, height) = loader.getExpectedDimensions()
    val ijScale = scale.calculateScalingFactorByOriginalDimensions(width, height)
    return Bounds(0, 0, width = (width * ijScale).toInt(), height = (height * ijScale).toInt())
  }

  override val width: Int = loader.getExpectedDimensions().first
  override val height: Int = loader.getExpectedDimensions().second
}