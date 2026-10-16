// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.icons.impl.intellij.rendering.images

import com.intellij.openapi.util.IconLoader
import com.intellij.platform.icons.rendering.Dimensions
import com.intellij.platform.icons.rendering.ImageModifiers
import com.intellij.ui.RetrievableIcon
import com.intellij.ui.icons.CachedImageIcon
import com.intellij.ui.icons.RgbImageFilterSupplier
import com.intellij.ui.scale.DerivedScaleType
import com.intellij.ui.scale.ScaleContext
import com.intellij.ui.scale.ScaleType
import com.intellij.ui.svg.replaceCachedImageIcons
import com.intellij.util.SVGLoader
import java.awt.Image
import java.awt.image.RGBImageFilter
import javax.swing.Icon
import javax.swing.ImageIcon

internal class LegacyIconImageResourceHolder(
  private val backingLegacyIcon: Icon
): SwingImageResourceHolder {
  override fun getExpectedDimensions(imageModifiers: ImageModifiers?): Dimensions {
    return Dimensions(backingLegacyIcon.iconWidth, backingLegacyIcon.iconHeight)
  }

  override fun getImage(scale: ScaleContext, imageModifiers: ImageModifiers?): Image? {
    val params = imageModifiers.toLoadParameters()
    val svgPatcher = params.colorPatcher
    val icon = if (svgPatcher != null && svgPatcher !== SVGLoader.colorPatcherProvider) {
      replaceCachedImageIcons(backingLegacyIcon) { cachedImageIcon ->
        cachedImageIcon.createWithPatcher(svgPatcher, isDark = params.isDark, useStroke = params.isStroke)
      } ?: backingLegacyIcon
    } else backingLegacyIcon
    val filtered = if (params.filters.isNotEmpty()) {
        IconLoader.filterIcon(icon = icon, filterSupplier = object : RgbImageFilterSupplier {
            override fun getFilter() = params.filters.first() as RGBImageFilter
        })
    } else icon
    val origin = filtered.origin()
    val context = when (origin) {
      is CachedImageIcon -> scale.withObjectScaleOf(origin)
      is ImageIcon -> scale
      else -> scale.toSystemScale()
    }
    return IconLoader.toImage(origin, context)
  }
}

private fun ScaleContext.withObjectScaleOf(icon: CachedImageIcon): ScaleContext {
  val iconScale = icon.getObjScale() ?: return this
  return copy<ScaleContext>().apply { setScale(ScaleType.OBJ_SCALE.of(getScale(ScaleType.OBJ_SCALE) * iconScale)) }
}

private fun Icon.origin(): Icon = generateSequence(this) { (it as? RetrievableIcon)?.retrieveIcon() }.take(11).last()

private fun ScaleContext.toSystemScale(): ScaleContext {
  val pixelScale = getScale(DerivedScaleType.PIX_SCALE)
  return ScaleContext.createIdentity().apply { setScale(ScaleType.SYS_SCALE.of(pixelScale)) }
}