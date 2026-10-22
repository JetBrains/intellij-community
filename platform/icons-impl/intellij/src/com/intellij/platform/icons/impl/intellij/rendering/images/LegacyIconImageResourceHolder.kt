// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.icons.impl.intellij.rendering.images

import com.intellij.openapi.util.IconLoader
import com.intellij.openapi.util.findIconUsingNewImplementation
import com.intellij.ui.RetrievableIcon
import com.intellij.ui.scale.JBUIScale
import com.intellij.ui.scale.ScaleType
import com.intellij.platform.icons.impl.rendering.DefaultImageModifiers
import com.intellij.platform.icons.modifiers.SizeVariantPolicy
import kotlin.math.roundToInt
import com.intellij.ui.icons.CachedImageIcon
import com.intellij.ui.icons.RgbImageFilterSupplier
import com.intellij.ui.scale.ScaleContext
import com.intellij.platform.icons.rendering.Dimensions
import com.intellij.platform.icons.rendering.ImageModifiers
import com.intellij.platform.icons.rendering.ThemeContext
import java.awt.Image
import java.awt.image.RGBImageFilter
import javax.swing.Icon

internal class LegacyIconImageResourceHolder(
  private val backingLegacyIcon: Icon
): SwingImageResourceHolder {
  override fun getExpectedDimensions(): Dimensions {
    return Dimensions(backingLegacyIcon.iconWidth, backingLegacyIcon.iconHeight)
  }

  /**
   * The legacy icon applies its own color patcher (the theme palette, as a rule) and the global filter itself. They come
   * from the global theme state of the legacy icons, not from [theme]. So [theme] does not change the colors of a legacy
   * icon. Only the dark state of the theme applies, through the dark state of [imageModifiers].
   */
  override fun getImage(scale: ScaleContext, imageModifiers: ImageModifiers?, theme: ThemeContext): Image? {
    val (source, sourceScale) = sizeVariant(scale, imageModifiers.sizeVariantPolicy()) ?: (backingLegacyIcon to scale)
    // `createWithPatcher` replaces the own patcher of the legacy icon, so our patches run on top of it.
    val rootPatcher = (source as? CachedImageIcon)?.svgColorPatcher
    val params = imageModifiers.toLoadParameters(rootPatcher = rootPatcher, globalFilter = null)
    val svgPatcher = imageModifiers.ownColorPatcher(rootPatcher = rootPatcher)
    // Without an explicit dark state, the legacy icon keeps its own one. It follows the theme unless it is set.
    // A stroked icon loads the light artwork, see `toLoadParameters`.
    val darkOverride = (imageModifiers as? DefaultImageModifiers)?.darkOverride?.takeUnless { params.isStroke }
    val icon = when {
      source is CachedImageIcon && svgPatcher != null -> {
        source.createWithPatcher(svgPatcher, isDark = if (params.isStroke) false else darkOverride, useStroke = params.isStroke)
      }
      darkOverride != null -> IconLoader.getDarkIcon(source, darkOverride)
      else -> source
    }
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