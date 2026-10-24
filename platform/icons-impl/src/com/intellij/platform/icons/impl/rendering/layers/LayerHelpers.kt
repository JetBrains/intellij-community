// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.icons.impl.rendering.layers

import com.intellij.platform.icons.impl.layers.findModifier
import com.intellij.platform.icons.impl.modifiers.ColorFilterModifier
import com.intellij.platform.icons.impl.modifiers.DarkModifier
import com.intellij.platform.icons.impl.modifiers.SizeVariantModifier
import com.intellij.platform.icons.impl.modifiers.StrokeModifier
import com.intellij.platform.icons.impl.modifiers.SvgPatcherModifier
import com.intellij.platform.icons.impl.rendering.DefaultImageModifiers
import com.intellij.platform.icons.impl.rendering.DefaultRenderingContext
import com.intellij.platform.icons.impl.rendering.darkState
import com.intellij.platform.icons.impl.rendering.legacyImageModifiers
import com.intellij.platform.icons.layers.IconLayer
import com.intellij.platform.icons.modifiers.SizeVariantPolicy
import com.intellij.platform.icons.rendering.ScalingContext
import kotlin.math.ceil

/**
 * The image modifiers of this layer: its own modifiers, else the ones it inherits from [renderingContext].
 *
 * The dark state is the innermost explicit one (a [DarkModifier] of this layer, else the inherited
 * [DefaultImageModifiers.darkOverride]). Without one, it is the dark state of the theme ([darkState]), read now. A
 * theme that does not supply a dark state leaves the inherited [DefaultImageModifiers.isDark]. The renderers call this
 * when they are built, and an icon renderer rebuilds them when the theme digest changes, so the dark state follows the
 * theme.
 *
 * The other theme inputs (the color palette and the global filter) are not part of the image modifiers. The image
 * loaders read them from the theme of the rendering context.
 */
fun IconLayer.generateImageModifiers(renderingContext: DefaultRenderingContext? = null): DefaultImageModifiers {
    val theme = renderingContext?.theme
    val defaults = renderingContext?.defaultImageModifiers ?: theme?.legacyImageModifiers()
    return DefaultImageModifiers(
        colorFilter = findModifier<ColorFilterModifier>()?.colorFilter ?: defaults?.colorFilter,
        svgPatcher = findModifier<SvgPatcherModifier>()?.svgPatcher ?: defaults?.svgPatcher,
        isDark = theme?.darkState() ?: defaults?.isDark ?: false,
        stroke = findModifier<StrokeModifier>()?.color ?: defaults?.stroke,
        sizeVariantPolicy = findModifier<SizeVariantModifier>()?.policy ?: defaults?.sizeVariantPolicy ?: SizeVariantPolicy.Exact,
        darkOverride = findModifier<DarkModifier>()?.dark ?: defaults?.darkOverride,
    )
}

fun ScalingContext.applyTo(px: Int): Int = applyTo(px.toDouble())

fun ScalingContext.applyTo(px: Double): Int = ceil(px * displayDensity * contextScale).toInt()

fun ScalingContext.applyTo(px: Int?): Int? {
    if (px == null) return null
    return applyTo(px)
}
