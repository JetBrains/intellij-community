// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.icons.impl.intellij.rendering.images

import com.intellij.platform.icons.impl.intellij.rendering.IntelliJImageModifiers
import com.intellij.ui.icons.LoadIconParameters
import com.intellij.ui.scale.ScaleContext
import com.intellij.platform.icons.impl.intellij.rendering.toAwtFilter
import com.intellij.platform.icons.impl.intellij.rendering.toIJPatcher
import com.intellij.platform.icons.impl.patchers.DefaultSvgPatcher
import com.intellij.platform.icons.impl.rendering.DefaultImageModifiers
import com.intellij.platform.icons.rendering.Dimensions
import com.intellij.platform.icons.rendering.ImageModifiers
import java.awt.Image
import java.awt.image.ImageFilter

internal interface SwingImageResourceHolder {
  fun getImage(scale: ScaleContext, imageModifiers: ImageModifiers?): Image?
  fun getExpectedDimensions(): Dimensions
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
    rootPatcher = ijModifiers?.legacyPatcherProvider,
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