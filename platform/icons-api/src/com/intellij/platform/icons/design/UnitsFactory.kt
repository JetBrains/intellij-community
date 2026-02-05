// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.icons.design

import com.intellij.platform.icons.scale.FactorScale
import com.intellij.platform.icons.scale.FillAreaScale
import com.intellij.platform.icons.scale.FitAreaScale
import java.awt.Color
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
interface UnitsFactory {
    fun align(verticalAlign: IconVerticalAlign, horizontalAlign: IconHorizontalAlign): IconAlign

    fun margin(top: IconUnit, left: IconUnit, bottom: IconUnit, right: IconUnit): IconMargin

    fun circle(radius: IconUnit): Circle

    fun rectangle(width: IconUnit, height: IconUnit): Rectangle

    fun sRGB(red: Float, green: Float, blue: Float, alpha: Float): SRGBColor

    fun sRGBHex(hex: String): SRGBColor

    fun dp(value: Double): DisplayPoint

    fun px(value: Int): Pixel

    fun factorScale(factor: Double): FactorScale

    fun fitAreaScale(width: IconUnit, height: IconUnit, relative: Boolean = true): FitAreaScale

    fun fillAreaScale(width: IconUnit, height: IconUnit, relative: Boolean = true): FillAreaScale

    fun toAwtColor(color: com.intellij.platform.icons.design.Color): Color
}
