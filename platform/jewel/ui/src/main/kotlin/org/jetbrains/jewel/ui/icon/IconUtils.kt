// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jewel.ui.icon

import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import com.intellij.platform.icons.design.Circle
import com.intellij.platform.icons.design.DisplayPoint
import com.intellij.platform.icons.design.IconAlign
import com.intellij.platform.icons.design.IconDesigner
import com.intellij.platform.icons.design.IconUnit
import com.intellij.platform.icons.design.Rectangle
import com.intellij.platform.icons.design.Shape
import com.intellij.platform.icons.design.badge
import com.intellij.platform.icons.design.circle
import com.intellij.platform.icons.design.dp
import com.intellij.platform.icons.design.rectangle
import com.intellij.platform.icons.filters.ColorFilter
import com.intellij.platform.icons.impl.design.DefaultSRGB
import com.intellij.platform.icons.impl.filters.TintColorFilter
import com.intellij.platform.icons.modifiers.IconModifier
import com.intellij.platform.icons.modifiers.stroke
import com.intellij.platform.icons.modifiers.tintColor
import com.intellij.platform.icons.scale.FillAreaScale
import com.intellij.platform.icons.scale.FitAreaScale
import com.intellij.platform.icons.scale.fillArea
import com.intellij.platform.icons.scale.fitArea
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.jewel.foundation.ExperimentalJewelApi

@ExperimentalJewelApi
@ApiStatus.Experimental
public fun IconModifier.tintColor(composeColor: Color, blendMode: BlendMode = BlendMode.SrcIn): IconModifier =
    tintColor(composeColor.toIconsColor(), blendMode.toIconsBlendMode())

@ExperimentalJewelApi @ApiStatus.Experimental public fun circle(radius: Dp): Circle = circle(radius.toIconsDp())

@ExperimentalJewelApi
@ApiStatus.Experimental
public fun rectangle(width: Dp, heigth: Dp): Rectangle = rectangle(width.toIconsDp(), heigth.toIconsDp())

@ExperimentalJewelApi
@ApiStatus.Experimental
public fun fitArea(width: Dp, heigth: Dp): FitAreaScale = fitArea(width.toIconsDp(), heigth.toIconsDp())

@ExperimentalJewelApi
@ApiStatus.Experimental
public fun fillArea(width: Dp, heigth: Dp): FillAreaScale = fillArea(width.toIconsDp(), heigth.toIconsDp())

@ExperimentalJewelApi @ApiStatus.Experimental public fun Dp.toIconsDp(): DisplayPoint = this.value.dp

@ExperimentalJewelApi
@ApiStatus.Experimental
public fun IconModifier.stroke(composeColor: Color): IconModifier = stroke(composeColor.toIconsColor())

@ExperimentalJewelApi
@ApiStatus.Experimental
public fun IconDesigner.badge(
    color: Color,
    shape: Shape = circle(2.8.dp),
    align: IconAlign = IconAlign.TopRight,
    cutout: IconUnit = 1.2.dp,
    modifier: IconModifier = IconModifier,
) {
    badge(color.toIconsColor(), shape, align, cutout, modifier)
}

@ExperimentalJewelApi
@ApiStatus.Experimental
public fun ColorFilter.toCompose(): androidx.compose.ui.graphics.ColorFilter =
    when (this) {
        is TintColorFilter -> androidx.compose.ui.graphics.ColorFilter.tint(color.toCompose(), blendMode.toCompose())
        else -> error("Unsupported color filter $this")
    }

@ExperimentalJewelApi
@ApiStatus.Experimental
public fun com.intellij.platform.icons.design.Color.toCompose(): Color =
    when (this) {
        is DefaultSRGB -> Color(red, green, blue, alpha)
        else -> error("Unsupported color $this")
    }

@ExperimentalJewelApi
@ApiStatus.Experimental
public fun com.intellij.platform.icons.design.BlendMode.toCompose(): BlendMode =
    when (this) {
        com.intellij.platform.icons.design.BlendMode.SrcIn -> BlendMode.SrcIn
        com.intellij.platform.icons.design.BlendMode.Color -> BlendMode.Color
        com.intellij.platform.icons.design.BlendMode.Hue -> BlendMode.Hue
        com.intellij.platform.icons.design.BlendMode.Luminosity -> BlendMode.Luminosity
        com.intellij.platform.icons.design.BlendMode.Saturation -> BlendMode.Saturation
        com.intellij.platform.icons.design.BlendMode.Multiply -> BlendMode.Multiply
    }

@ExperimentalJewelApi
@ApiStatus.Experimental
public fun BlendMode.toIconsBlendMode(): com.intellij.platform.icons.design.BlendMode =
    when (this) {
        BlendMode.SrcIn -> com.intellij.platform.icons.design.BlendMode.SrcIn
        BlendMode.Color -> com.intellij.platform.icons.design.BlendMode.Color
        BlendMode.Hue -> com.intellij.platform.icons.design.BlendMode.Hue
        BlendMode.Luminosity -> com.intellij.platform.icons.design.BlendMode.Luminosity
        BlendMode.Saturation -> com.intellij.platform.icons.design.BlendMode.Saturation
        BlendMode.Multiply -> com.intellij.platform.icons.design.BlendMode.Multiply
        else -> error("Unsupported Compose blend mode $this")
    }

@ExperimentalJewelApi
@ApiStatus.Experimental
public fun Color.toIconsColor(): com.intellij.platform.icons.design.Color = DefaultSRGB(red, green, blue, alpha)
