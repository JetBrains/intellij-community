// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.icons.impl.intellij.rendering.images

import com.intellij.platform.icons.impl.intellij.rendering.IntelliJImageModifiers
import com.intellij.util.SVGLoader
import com.intellij.platform.icons.impl.intellij.rendering.toAwtFilter
import com.intellij.platform.icons.impl.intellij.rendering.toIJPatcher
import com.intellij.platform.icons.impl.patchers.DefaultSvgPatcher
import com.intellij.platform.icons.impl.rendering.DefaultImageModifiers
import com.intellij.platform.icons.impl.rendering.PaintedSize
import com.intellij.platform.icons.modifiers.SizeVariantPolicy
import com.intellij.platform.icons.rendering.Dimensions
import com.intellij.platform.icons.rendering.ImageModifiers
import com.intellij.ui.icons.LoadIconParameters
import com.intellij.ui.scale.ScaleContext
import java.awt.Component
import java.awt.Image
import java.awt.image.ImageFilter

internal interface SwingImageResourceHolder {
  /**
   * The image rasterized at the pixel scale of [scale].
   *
   * @param painted the logical size that the image is painted at, which selects a `@WxH` size variant; `null` for no variant
   * @param component the component the image is painted in, if known; passed to a legacy icon that paints the image
   */
  fun getImage(scale: ScaleContext, painted: PaintedSize?, imageModifiers: ImageModifiers?, component: Component? = null): Image?
  /**
   * The design size of the image with [imageModifiers], without the IDE zoom: the renderer applies the zoom. The
   * modifiers can select an artwork of another size, for example the dark one.
   */
  fun getExpectedDimensions(imageModifiers: ImageModifiers?): Dimensions
}

internal fun ImageModifiers?.toLoadParameters(): LoadIconParameters {
  val filters = mutableListOf<ImageFilter>()
  val colorFilter = this?.colorFilter
  if (colorFilter != null) {
    filters.add(colorFilter.toAwtFilter())
  }
  val knownModifiers = this as? DefaultImageModifiers
  val ijModifiers = this as? IntelliJImageModifiers
  val stroke = knownModifiers?.stroke
  // The stroke patch is combined per resolved path rather than here, because which patch an icon takes depends on
  // whether the loader ends up resolving a hand-authored stroke variant for it.
  val colorPatcher = toIJPatcher(
    stroke = stroke,
    patcher = this?.svgPatcher as? DefaultSvgPatcher,
    rootPatcher = ijModifiers?.legacyPatcherProvider ?: SVGLoader.colorPatcherProvider,
  )
  val isStroke = stroke != null
  return LoadIconParameters(
    filters = filters,
    // A stroked icon loads its light artwork even in a dark theme. The palette describes the light variants, so
    // resolving `_dark` first would hand the patcher colors it does not know and the icon would keep its authored
    // ones — and the artwork is about to be recolored wholesale anyway, so which variant it started from is moot.
    isDark = !isStroke && knownModifiers?.isDark == true,
    colorPatcher = colorPatcher,
    isStroke = isStroke
  )
}
/** The size-variant policy resolved for the image ([SizeVariantPolicy.Exact] if the modifiers do not say). */
internal fun ImageModifiers?.sizeVariantPolicy(): SizeVariantPolicy =
  (this as? DefaultImageModifiers)?.sizeVariantPolicy ?: SizeVariantPolicy.Exact
