// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.icons.impl.intellij.rendering.images

import com.intellij.openapi.util.IconLoader
import com.intellij.openapi.util.findIconUsingNewImplementation
import com.intellij.ui.RetrievableIcon
import com.intellij.ui.scale.JBUIScale
import com.intellij.ui.scale.ScaleType
import com.intellij.platform.icons.modifiers.SizeVariantPolicy
import kotlin.math.roundToInt
import com.intellij.ui.icons.CachedImageIcon
import com.intellij.ui.icons.RgbImageFilterSupplier
import com.intellij.ui.scale.ScaleContext
import com.intellij.platform.icons.rendering.Dimensions
import com.intellij.platform.icons.rendering.ImageModifiers
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
    val (source, sourceScale) = sizeVariant(scale, imageModifiers.sizeVariantPolicy()) ?: (backingLegacyIcon to scale)
    val icon = if (source is CachedImageIcon && svgPatcher != null) {
      source.createWithPatcher(svgPatcher, isDark = params.isDark, useStroke = params.isStroke)
    } else source
    val filtered = if (params.filters.isNotEmpty()) {
        IconLoader.filterIcon(icon = icon, filterSupplier = object : RgbImageFilterSupplier {
            override fun getFilter() = params.filters.first() as RGBImageFilter
        })
    } else icon
    return IconLoader.toImage(filtered, sourceScale)
  }

  /**
   * The hand-drawn `@WxH` variant [policy] selects for a backing `CachedImageIcon` at the logical size [scale] paints it at (see
   * [SizeVariantSupport]), carrying the original icon's modifications (dark state, filters, patcher) as legacy
   * `loadIconCustomVersion` does via `withAnotherIconModifications`; `null` if none applies.
   */
  private fun sizeVariant(scale: ScaleContext, policy: SizeVariantPolicy): Pair<Icon, ScaleContext>? {
    if (policy == SizeVariantPolicy.None || scale.getScale(ScaleType.OBJ_SCALE) == 1.0) return null
    val cached = backingLegacyIcon as? CachedImageIcon ?: (backingLegacyIcon as? RetrievableIcon)?.retrieveIcon() as? CachedImageIcon
                 ?: return null
    // the theme path patch is applied lazily, on the first size request
    val ownWidth = cached.iconWidth
    val ownHeight = cached.iconHeight
    if (ownWidth <= 0 || ownHeight <= 0) return null
    val expected = getExpectedDimensions()
    val logicalWidth = SizeVariantSupport.logicalSize(expected.width, scale)
    val logicalHeight = SizeVariantSupport.logicalSize(expected.height, scale)
    val (path, classLoader) = cached.getCoords() ?: return null
    val zoom = JBUIScale.scale(1f)
    val baseDesignWidth = (ownWidth / zoom).roundToInt()
    val baseDesignHeight = (ownHeight / zoom).roundToInt()
    val (width, height) = SizeVariantSupport.chooseVariant(
      policy, logicalWidth, logicalHeight, ownWidth, ownHeight, baseDesignWidth, baseDesignHeight,
    ) { w, h -> SizeVariantSupport.existingVariantPath(path, classLoader, w, h) != null } ?: return null
    val variantPath = SizeVariantSupport.existingVariantPath(path, classLoader, width, height) ?: return null
    val found = findIconUsingNewImplementation(variantPath, classLoader) as? CachedImageIcon ?: return null
    // the legacy acceptance check
    if (found.iconWidth != JBUIScale.scale(width) || found.iconHeight != JBUIScale.scale(height)) return null
    return found.withAnotherIconModifications(cached) to SizeVariantSupport.variantScale(scale, logicalWidth, width)
  }
}