// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.icons.impl.rendering

import com.intellij.platform.icons.rendering.Dimensions
import com.intellij.platform.icons.rendering.IconRenderer
import com.intellij.platform.icons.rendering.ScalingContext
import com.intellij.platform.icons.scale.FactorScale
import com.intellij.platform.icons.scale.IconScale
import com.intellij.platform.icons.impl.design.toFactor
import kotlin.math.roundToInt

fun IconRenderer.resolve(density: Float, scale: IconScale?): ResolvedScalingContext = resolve(density, scale, 1f)

/**
 * The size of the icon and the scaling context to render it at [density], with its [scale]. [contextScale] scales every
 * unit of the icon, for example the IDE zoom of a Swing renderer. It is the zoom of the context, see
 * [DefaultScalingContext.zoom]. A [FactorScale] is the scale of the whole icon: the
 * icon measures and paints in a context with that factor, see [DefaultScalingContext.iconScale]. So a nested fit with
 * `relative = false` has the same size in both, also with its `upscale` or `downscale` limit. Another scale, such as a
 * fit of the whole icon, does not scale a nested fit with `relative = false`.
 */
fun IconRenderer.resolve(density: Float, scale: IconScale?, contextScale: Float): ResolvedScalingContext {
    if (scale is FactorScale) {
        val factor = scale.factor.toFloat()
        val context = DefaultScalingContext(density, contextScale * factor, iconScale = factor, zoom = contextScale)
        return ResolvedScalingContext(calculateUsedDimensions(context), context)
    }
    val unscaled = DefaultScalingContext(density, contextScale, iconScale = 1f, zoom = contextScale)
    val original = calculateUsedDimensions(unscaled)
    val factor = scale?.toFactor(unscaled, original.width, original.height) ?: 1f
    return ResolvedScalingContext(
        Dimensions((original.width * factor).roundToInt(), (original.height * factor).roundToInt()),
        DefaultScalingContext(density, contextScale * factor, iconScale = 1f, zoom = contextScale),
    )
}

class ResolvedScalingContext(val finalDimensions: Dimensions, val context: ScalingContext)
