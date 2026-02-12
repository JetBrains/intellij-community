// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.icons.impl.intellij.rendering.images

import com.intellij.ui.icons.LoadIconParameters
import com.intellij.ui.scale.ScaleContext
import org.jetbrains.icons.impl.intellij.rendering.toAwtFilter
import org.jetbrains.icons.impl.intellij.rendering.toIJPatcher
import org.jetbrains.icons.impl.rendering.DefaultImageModifiers
import org.jetbrains.icons.modifiers.svgPatcher
import org.jetbrains.icons.patchers.combineWith
import org.jetbrains.icons.rendering.Dimensions
import org.jetbrains.icons.rendering.ImageModifiers
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
  val strokePatcher = knownModifiers?.stroke?.let { stroke ->
    svgPatcher {
      replace("fill", "transparent")
      add("stroke", stroke.toHex())
    }
  }
  val colorPatcher = (this?.svgPatcher combineWith strokePatcher)?.toIJPatcher()
  return LoadIconParameters(
    filters = filters,
    isDark = knownModifiers?.isDark ?: false,
    colorPatcher = colorPatcher,
    isStroke = knownModifiers?.stroke != null
  )
}