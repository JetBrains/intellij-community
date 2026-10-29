// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jewel.ui.icon

import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asComposeColorFilter
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
import com.intellij.platform.icons.impl.filters.GrayColorFilter
import com.intellij.platform.icons.impl.filters.TintColorFilter
import com.intellij.platform.icons.modifiers.IconModifier
import com.intellij.platform.icons.modifiers.stroke
import com.intellij.platform.icons.modifiers.tintColor
import com.intellij.platform.icons.scale.FillAreaScale
import com.intellij.platform.icons.scale.FitAreaScale
import com.intellij.platform.icons.scale.fillArea
import com.intellij.platform.icons.scale.fitArea
import kotlin.math.pow
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.jewel.foundation.ExperimentalJewelApi
import org.jetbrains.skia.ColorFilter as SkiaColorFilter
import org.jetbrains.skia.ColorMatrix

/**
 * Applies a tint of [composeColor] with [blendMode] to this [IconModifier], converting to the IntelliJ Platform Icons
 * color and blend-mode types.
 *
 * @param composeColor The Compose [Color] to use as the tint.
 * @param blendMode The [BlendMode] to use for blending. Defaults to [BlendMode.SrcIn].
 */
@ExperimentalJewelApi
@ApiStatus.Experimental
public fun IconModifier.tintColor(composeColor: Color, blendMode: BlendMode = BlendMode.SrcIn): IconModifier =
    tintColor(composeColor.toIconsColor(), blendMode.toIconsBlendMode())

/** Creates a [Circle] shape with the given [radius] for use in the IntelliJ Platform icon DSL. */
@ExperimentalJewelApi @ApiStatus.Experimental public fun circle(radius: Dp): Circle = circle(radius.toIconsDp())

@ExperimentalJewelApi
@ApiStatus.Experimental
/** Creates a [Rectangle] shape with the given dimensions for use in the IntelliJ Platform icon DSL. */
public fun rectangle(width: Dp, heigth: Dp): Rectangle = rectangle(width.toIconsDp(), heigth.toIconsDp())

@ExperimentalJewelApi
@ApiStatus.Experimental
/** Creates a [FitAreaScale] that fits the icon within the given dimensions. */
public fun fitArea(width: Dp, heigth: Dp): FitAreaScale = fitArea(width.toIconsDp(), heigth.toIconsDp())

@ExperimentalJewelApi
@ApiStatus.Experimental
/** Creates a [FillAreaScale] that fills the icon to the given dimensions. */
public fun fillArea(width: Dp, heigth: Dp): FillAreaScale = fillArea(width.toIconsDp(), heigth.toIconsDp())

/** Converts this Compose [Dp] value to an IntelliJ Platform Icons [DisplayPoint]. */
@ExperimentalJewelApi @ApiStatus.Experimental public fun Dp.toIconsDp(): DisplayPoint = this.value.dp

/** Applies a stroke with the given Compose [composeColor] to this [IconModifier]. */
@ExperimentalJewelApi
@ApiStatus.Experimental
public fun IconModifier.stroke(composeColor: Color): IconModifier = stroke(composeColor.toIconsColor())

/**
 * Adds a badge to this icon using a Compose [color], with configurable [shape], [align], [cutout], and [modifier].
 *
 * @param color The Compose color for the badge.
 * @param shape The shape of the badge. Defaults to a small circle.
 * @param align Where to position the badge. Defaults to [IconAlign.TopRight].
 * @param cutout The size of the cutout carved into the icon beneath the badge.
 * @param modifier Additional icon modifier to apply to the badge.
 */
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
/**
 * Converts this IntelliJ Platform [ColorFilter] to a Compose [ColorFilter][androidx.compose.ui.graphics.ColorFilter].
 */
public fun ColorFilter.toCompose(): androidx.compose.ui.graphics.ColorFilter =
    when (this) {
        is TintColorFilter -> androidx.compose.ui.graphics.ColorFilter.tint(color.toCompose(), blendMode.toCompose())
        is GrayColorFilter -> toSkia().asComposeColorFilter()
        else -> error("Unsupported color filter $this")
    }

/**
 * Builds a Skia filter with the pixels of `com.intellij.util.ui.GrayFilter`. A matrix computes the luminance and
 * scales the alpha, and a table applies the brightness and contrast curve. See
 * [com.intellij.platform.icons.filters.gray] for the steps.
 *
 * The result can differ from `GrayFilter` by one level, because `GrayFilter` truncates the luminance and the alpha to
 * integers and Skia rounds them. A positive contrast can differ more, because its curve has a jump at the gray value
 * 127. A difference of one level on the two sides of the jump gives two different results.
 */
private fun GrayColorFilter.toSkia(): SkiaColorFilter {
    val luminance = ColorMatrix(
        0.30f, 0.59f, 0.11f, 0f, 0f,
        0.30f, 0.59f, 0.11f, 0f, 0f,
        0.30f, 0.59f, 0.11f, 0f, 0f,
        0f, 0f, 0f, alpha / 100f, 0f,
    )
    val brightness = (brightness.toDouble().pow(3) / (100f * 100f)).toFloat()
    val contrast = contrast / 100f
    val curve = ByteArray(256) { value ->
        val bright =
            if (brightness >= 0) ((value + brightness * 255) / (1 + brightness)).toInt()
            else (value / (1 - brightness)).toInt()
        val result =
            when {
                contrast < 0 -> (127 + (bright - 127) * (contrast + 1)).toInt()
                bright >= 127 -> (bright + (255 - bright) * contrast).toInt()
                else -> (bright - bright * contrast).toInt()
            }
        result.toByte()
    }
    return SkiaColorFilter.makeComposed(
        SkiaColorFilter.makeTableARGB(null, curve, curve, curve),
        SkiaColorFilter.makeMatrix(luminance),
    )
}

@ExperimentalJewelApi
@ApiStatus.Experimental
/** Converts this IntelliJ Platform Icons [Color][com.intellij.platform.icons.design.Color] to a Compose [Color]. */
public fun com.intellij.platform.icons.design.Color.toCompose(): Color =
    when (this) {
        is DefaultSRGB -> Color(red, green, blue, alpha)
        else -> error("Unsupported color $this")
    }

@ExperimentalJewelApi
@ApiStatus.Experimental
/**
 * Converts this IntelliJ Platform Icons [BlendMode][com.intellij.platform.icons.design.BlendMode] to a Compose
 * [BlendMode].
 */
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
/**
 * Converts this Compose [BlendMode] to the equivalent IntelliJ Platform Icons
 * [BlendMode][com.intellij.platform.icons.design.BlendMode].
 */
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
/** Converts this Compose [Color] to an IntelliJ Platform Icons [Color][com.intellij.platform.icons.design.Color]. */
public fun Color.toIconsColor(): com.intellij.platform.icons.design.Color = DefaultSRGB(red, green, blue, alpha)
