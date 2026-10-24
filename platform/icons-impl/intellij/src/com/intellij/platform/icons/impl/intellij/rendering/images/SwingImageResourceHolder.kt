// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.icons.impl.intellij.rendering.images

import com.intellij.ui.icons.LoadIconParameters
import com.intellij.ui.scale.ScaleContext
import com.intellij.platform.icons.impl.intellij.rendering.toAwtFilter
import com.intellij.platform.icons.impl.intellij.rendering.toIJPatcher
import com.intellij.platform.icons.impl.patchers.DefaultSvgPatcher
import com.intellij.platform.icons.impl.rendering.DefaultImageModifiers
import com.intellij.platform.icons.rendering.Dimensions
import com.intellij.platform.icons.modifiers.SizeVariantPolicy
import com.intellij.platform.icons.rendering.ImageModifiers
import com.intellij.platform.icons.impl.intellij.rendering.intelliJColorPalette
import com.intellij.platform.icons.impl.intellij.rendering.intelliJGlobalFilter
import com.intellij.platform.icons.rendering.ThemeContext
import com.intellij.util.SVGLoader
import java.awt.Image
import java.awt.image.ImageFilter

internal interface SwingImageResourceHolder {
  /** The image at [scale] with [imageModifiers] and the theme inputs of [theme], read now. */
  fun getImage(scale: ScaleContext, imageModifiers: ImageModifiers?, theme: ThemeContext): Image?
  fun getExpectedDimensions(): Dimensions
}

/**
 * The legacy loader parameters for these modifiers, with the theme inputs of [theme], read now.
 *
 * The theme color palette is the root patcher: the patcher the image has without our modifiers. See [toLoadParameters].
 * The theme global filter runs before the color filter of the modifiers, as in legacy `CachedImageIcon`.
 */
internal fun ImageModifiers?.toLoadParameters(theme: ThemeContext): LoadIconParameters =
  toLoadParameters(rootPatcher = theme.intelliJColorPalette(), globalFilter = theme.intelliJGlobalFilter())

/**
 * The legacy loader parameters for these modifiers.
 *
 * [rootPatcher] is the patcher the image has without our modifiers: the theme palette for a native image. The stroke
 * and SVG patches of the modifiers run on top of it. A stroked image does not take it: the stroke patch matches the
 * authored light colors, and a palette applied first replaces them. Legacy `toStrokeIcon` also drops the palette.
 *
 * [globalFilter] runs before the color filter of the modifiers, as in legacy `CachedImageIcon`.
 */
internal fun ImageModifiers?.toLoadParameters(
  rootPatcher: SVGLoader.SvgElementColorPatcherProvider?,
  globalFilter: ImageFilter?,
): LoadIconParameters {
  val filters = mutableListOf<ImageFilter>()
  if (globalFilter != null) {
    filters.add(globalFilter)
  }
  val colorFilter = this?.colorFilter
  if (colorFilter != null) {
    filters.add(colorFilter.toAwtFilter())
  }
  val knownModifiers = this as? DefaultImageModifiers
  val stroke = knownModifiers?.stroke
  val isStroke = stroke != null
  return LoadIconParameters(
    filters = filters,
    // A stroked icon loads its light artwork even in a dark theme. The palette describes the light variants, so
    // resolving `_dark` first would hand the patcher colors it does not know and the icon would keep its authored
    // ones — and the artwork is about to be recolored wholesale anyway, so which variant it started from is moot.
    isDark = !isStroke && knownModifiers?.isDark == true,
    colorPatcher = ownColorPatcher(rootPatcher) ?: rootPatcher,
    isStroke = isStroke
  )
}

/**
 * The color patcher for the stroke and SVG patches of these modifiers, on top of [rootPatcher] (see [toLoadParameters]).
 * Returns `null` if the modifiers patch nothing.
 */
internal fun ImageModifiers?.ownColorPatcher(
  rootPatcher: SVGLoader.SvgElementColorPatcherProvider?,
): SVGLoader.SvgElementColorPatcherProvider? {
  val stroke = (this as? DefaultImageModifiers)?.stroke
  // The stroke patch is combined per resolved path rather than here, because which patch an icon takes depends on
  // whether the loader ends up resolving a hand-authored stroke variant for it.
  return toIJPatcher(
    stroke = stroke,
    patcher = this?.svgPatcher as? DefaultSvgPatcher,
    rootPatcher = if (stroke == null) rootPatcher else null,
  )
}

/** The size-variant policy resolved for the image ([SizeVariantPolicy.Exact] if the modifiers do not say). */
internal fun ImageModifiers?.sizeVariantPolicy(): SizeVariantPolicy =
  (this as? DefaultImageModifiers)?.sizeVariantPolicy ?: SizeVariantPolicy.Exact
