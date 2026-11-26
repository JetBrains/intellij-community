// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.icons.impl.rendering

import com.intellij.platform.icons.rendering.Dimensions
import com.intellij.platform.icons.rendering.IconRenderer
import com.intellij.platform.icons.rendering.ScalingContext
import com.intellij.platform.icons.scale.FactorScale
import com.intellij.platform.icons.scale.IconScale
import kotlin.math.roundToInt

fun IconRenderer.resolve(density: Float, scale: IconScale?): ResolvedScalingContext = resolve(density, scale, 1f)

/**
 * The size of the icon and the scaling context to render it at [density], with its [scale]. [contextScale] scales every
 * unit of the icon, for example the IDE zoom of a Swing renderer.
 */
fun IconRenderer.resolve(density: Float, scale: IconScale?, contextScale: Float): ResolvedScalingContext {
    if (scale is FactorScale) {
        // The paint folds the factor into the context scale. The size is measured in the same context, so that a layer
        // that ignores the context scale, such as a fit with `relative = false`, has the same size in both.
        val context = DefaultScalingContext(density, contextScale * scale.factor.toFloat())
        return ResolvedScalingContext(calculateUsedDimensions(context), context)
    }
    val original = calculateUsedDimensions(DefaultScalingContext(density, contextScale))
    val factor = scale?.toFactor(density, original.width, original.height, contextScale) ?: 1f
    return ResolvedScalingContext(
        Dimensions((original.width * factor).roundToInt(), (original.height * factor).roundToInt()),
        DefaultScalingContext(density, contextScale * factor),
    )
}

class ResolvedScalingContext(val finalDimensions: Dimensions, val context: ScalingContext)
