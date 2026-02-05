// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.icons.impl.modifiers

import com.intellij.platform.icons.design.IconUnit
import com.intellij.platform.icons.impl.design.DefaultDisplayPoint
import com.intellij.platform.icons.impl.design.DefaultPixel
import com.intellij.platform.icons.impl.rendering.layers.LayerLayout
import com.intellij.platform.icons.modifiers.IconModifier
import com.intellij.platform.icons.rendering.ScalingContext
import kotlin.math.roundToInt
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
fun IconModifier.applyTo(layout: LayerLayout): LayerLayout =
    if (this is ApplyableIconModifier) {
        applyTo(layout)
    } else {
        layout
    }

fun IconUnit.asFractionalPixels(scaling: ScalingContext, additionalScale: Float = 1f): Float =
    when (this) {
        is DefaultPixel -> scaling.contextScale * value * additionalScale
        is DefaultDisplayPoint -> scaling.displayDensity * scaling.contextScale * value * additionalScale
        else -> error("Unsupported IconUnit: $this")
    }.toFloat()

fun IconUnit.asPixels(scaling: ScalingContext, additionalScale: Float = 1f): Int =
    asFractionalPixels(scaling, additionalScale).roundToInt()
