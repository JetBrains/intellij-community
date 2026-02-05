// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.icons.impl.rendering.layers

import org.jetbrains.icons.modifiers.ColorFilterModifier
import org.jetbrains.icons.modifiers.StrokeModifier
import org.jetbrains.icons.modifiers.SvgPatcherModifier
import org.jetbrains.icons.rendering.ImageModifiers
import org.jetbrains.icons.rendering.RenderingContext
import org.jetbrains.icons.rendering.ScalingContext
import org.jetbrains.icons.layers.IconLayer
import org.jetbrains.icons.impl.layers.findModifier
import org.jetbrains.icons.impl.rendering.DefaultImageModifiers
import kotlin.math.ceil

fun IconLayer.generateImageModifiers(renderingContext: RenderingContext? = null): ImageModifiers {
  val defaults = renderingContext?.defaultImageModifiers
  val knownDefaults = defaults as? DefaultImageModifiers
  return DefaultImageModifiers(
    colorFilter = findModifier<ColorFilterModifier>()?.colorFilter ?: defaults?.colorFilter,
    svgPatcher = findModifier<SvgPatcherModifier>()?.svgPatcher ?: defaults?.svgPatcher,
    isDark = knownDefaults?.isDark ?: false,
    stroke = findModifier<StrokeModifier>()?.color ?: knownDefaults?.stroke
  )
}

fun RenderingContext.adjustTo(layer: IconLayer): RenderingContext {
  return copy(defaultImageModifiers = layer.generateImageModifiers(this))
}

fun ScalingContext.applyTo(px: Int): Int {
  return applyTo(px.toDouble())
}

fun ScalingContext.applyTo(px: Double): Int {
  return ceil(px * display).toInt()
}

fun ScalingContext.applyTo(px: Int?): Int? {
  if (px == null) return null
  return applyTo(px)
}