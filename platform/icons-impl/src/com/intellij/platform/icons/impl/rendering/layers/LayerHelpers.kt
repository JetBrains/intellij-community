// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.icons.impl.rendering.layers

import com.intellij.platform.icons.impl.layers.findModifier
import com.intellij.platform.icons.impl.modifiers.ColorFilterModifier
import com.intellij.platform.icons.impl.modifiers.StrokeModifier
import com.intellij.platform.icons.impl.modifiers.SvgPatcherModifier
import com.intellij.platform.icons.impl.rendering.DefaultImageModifiers
import com.intellij.platform.icons.impl.rendering.DefaultRenderingContext
import com.intellij.platform.icons.layers.IconLayer
import com.intellij.platform.icons.rendering.ScalingContext
import kotlin.math.ceil

fun IconLayer.generateImageModifiers(renderingContext: DefaultRenderingContext? = null): DefaultImageModifiers {
    val defaults = renderingContext?.defaultImageModifiers ?: renderingContext?.theme?.imageModifiers() as? DefaultImageModifiers
    return DefaultImageModifiers(
        colorFilter = findModifier<ColorFilterModifier>()?.colorFilter ?: defaults?.colorFilter,
        svgPatcher = findModifier<SvgPatcherModifier>()?.svgPatcher ?: defaults?.svgPatcher,
        isDark = defaults?.isDark ?: false,
        stroke = findModifier<StrokeModifier>()?.color ?: defaults?.stroke,
    )
}

fun ScalingContext.applyTo(px: Int): Int = applyTo(px.toDouble())

fun ScalingContext.applyTo(px: Double): Int = ceil(px * displayDensity * contextScale).toInt()

fun ScalingContext.applyTo(px: Int?): Int? {
    if (px == null) return null
    return applyTo(px)
}
