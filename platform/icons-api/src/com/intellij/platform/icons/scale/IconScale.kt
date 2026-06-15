// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.icons.scale

import com.intellij.platform.icons.IconManager
import com.intellij.platform.icons.design.IconUnit

/** Describes IconScale, can be constructed with [factor], [fitArea] or [fillArea] functions */
interface IconScale {
    /**
     * @param density - density of the screen
     * @param originalWidth - original width of the content
     * @param originalHeight - original height of the content
     * @param contextScale - context scale, this will be removed for fitArea and fillArea from the originalWidth and
     *   originalHeight to allow relative scale
     */
    fun toFactor(density: Float, originalWidth: Int, originalHeight: Int, contextScale: Float = 1f): Float

    companion object {
      @JvmField
      val Default: IconScale = factor(1f)
    }
}

interface FactorScale : IconScale {
    val factor: Double
}

interface FitAreaScale : IconScale {
    val width: IconUnit
    val height: IconUnit
    val relative: Boolean
}

interface FillAreaScale : IconScale {
    val width: IconUnit
    val height: IconUnit
    val relative: Boolean
}

/**
 * Factor scale will scale the layer, including the sublayers, by the given factor. The parent scale is also applied.
 */
fun factor(factor: Float): FactorScale = IconManager.units().factorScale(factor.toDouble())

/**
 * Factor scale will scale the layer, including the sublayers, by the given factor. The parent scale is also applied.
 */
fun factor(factor: Double): FactorScale = IconManager.units().factorScale(factor)

/**
 * Fit area scale will adjust the layer, including the sublayers, so that the resulting content is scaled to fit the
 * given area.
 *
 * @param relative - if true, the parent layer and icon scaling will affect the resulting scale, otherwise the scale
 *   will be in absolute units
 */
fun fitArea(width: IconUnit, height: IconUnit, relative: Boolean = true): FitAreaScale =
    IconManager.units().fitAreaScale(width, height, relative)

/**
 * Fill area scale will adjust the layer, including the sublayers, so that the resulting content is scaled to fill the
 * given area, any overflow shall be clipped.
 *
 * @param relative - if true, the parent layer and icon scaling will affect the resulting scale, otherwise the scale
 *   will be in absolute units
 */
fun fillArea(width: IconUnit, height: IconUnit, relative: Boolean = true): FillAreaScale =
    IconManager.units().fillAreaScale(width, height, relative)
