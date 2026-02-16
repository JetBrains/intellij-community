// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.icons.impl.intellij.rendering.images

import com.intellij.openapi.util.IconLoader
import com.intellij.ui.icons.CachedImageIcon
import com.intellij.ui.icons.RgbImageFilterSupplier
import com.intellij.ui.scale.ScaleContext
import org.jetbrains.icons.rendering.Dimensions
import org.jetbrains.icons.rendering.ImageModifiers
import java.awt.Image
import java.awt.image.RGBImageFilter
import javax.swing.Icon

internal class LegacyIconImageResourceHolder(
  private val backingLegacyIcon: Icon
): SwingImageResourceHolder {
  override fun getExpectedDimensions(): Dimensions {
    return Dimensions(backingLegacyIcon.iconWidth, backingLegacyIcon.iconHeight)
  }

  override fun getImage(scale: ScaleContext, imageModifiers: ImageModifiers?): Image? {
    val params = imageModifiers.toLoadParameters()
    val svgPatcher = params.colorPatcher
    val icon = if (backingLegacyIcon is CachedImageIcon && svgPatcher != null) {
      backingLegacyIcon.createWithPatcher(svgPatcher, isDark = params.isDark, useStroke = params.isStroke)
    } else backingLegacyIcon
    val filtered = if (params.filters.isNotEmpty()) {
        IconLoader.filterIcon(icon = icon, filterSupplier = object : RgbImageFilterSupplier {
            override fun getFilter() = params.filters.first() as RGBImageFilter
        })
    } else icon
    return IconLoader.toImage(filtered, scale)
  }
}