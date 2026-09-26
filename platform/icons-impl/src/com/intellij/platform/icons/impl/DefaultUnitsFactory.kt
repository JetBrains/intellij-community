// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.icons.impl

import com.intellij.platform.icons.design.Circle
import com.intellij.platform.icons.design.DisplayPoint
import com.intellij.platform.icons.design.IconAlign
import com.intellij.platform.icons.design.IconHorizontalAlign
import com.intellij.platform.icons.design.IconMargin
import com.intellij.platform.icons.design.IconUnit
import com.intellij.platform.icons.design.IconVerticalAlign
import com.intellij.platform.icons.design.Pixel
import com.intellij.platform.icons.design.Rectangle
import com.intellij.platform.icons.design.SRGBColor
import com.intellij.platform.icons.design.UnitsFactory
import com.intellij.platform.icons.impl.design.DefaultCircle
import com.intellij.platform.icons.impl.design.DefaultDisplayPoint
import com.intellij.platform.icons.impl.design.DefaultFactorScale
import com.intellij.platform.icons.impl.design.DefaultFillAreaScale
import com.intellij.platform.icons.impl.design.DefaultFitAreaScale
import com.intellij.platform.icons.impl.design.DefaultIconAlign
import com.intellij.platform.icons.impl.design.DefaultIconMargin
import com.intellij.platform.icons.impl.design.DefaultPixel
import com.intellij.platform.icons.impl.design.DefaultRectangle
import com.intellij.platform.icons.impl.design.DefaultSRGB
import com.intellij.platform.icons.scale.FactorScale
import com.intellij.platform.icons.scale.FillAreaScale
import com.intellij.platform.icons.scale.FitAreaScale
import java.awt.Color

object DefaultUnitsFactory : UnitsFactory {
    override fun align(verticalAlign: IconVerticalAlign, horizontalAlign: IconHorizontalAlign): IconAlign =
        DefaultIconAlign(verticalAlign, horizontalAlign)

    override fun margin(top: IconUnit, left: IconUnit, bottom: IconUnit, right: IconUnit): IconMargin =
        DefaultIconMargin(top, left, bottom, right)

    override fun circle(radius: IconUnit): Circle = DefaultCircle(radius)

    override fun rectangle(width: IconUnit, height: IconUnit): Rectangle = DefaultRectangle(width, height)

    override fun sRGB(red: Float, green: Float, blue: Float, alpha: Float): SRGBColor =
        DefaultSRGB(red, green, blue, alpha)

    override fun sRGBHex(hex: String): SRGBColor = DefaultSRGB.fromHex(hex)

    override fun dp(value: Double): DisplayPoint = DefaultDisplayPoint(value)

    override fun px(value: Int): Pixel = DefaultPixel(value)

    override fun factorScale(factor: Double): FactorScale = DefaultFactorScale(factor)

    override fun fitAreaScale(width: IconUnit, height: IconUnit, relative: Boolean): FitAreaScale =
        DefaultFitAreaScale(width, height, relative)

    override fun fillAreaScale(width: IconUnit, height: IconUnit, relative: Boolean): FillAreaScale =
        DefaultFillAreaScale(width, height, relative)

    override fun toAwtColor(color: com.intellij.platform.icons.design.Color): Color =
        when (color) {
            is DefaultSRGB -> Color(color.red, color.green, color.blue, color.alpha)
            else -> error("Unsupported color: $this")
        }
}
