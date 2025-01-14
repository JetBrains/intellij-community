package org.jetbrains.jewel.bridge.theme

import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.takeOrElse
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import org.jetbrains.jewel.bridge.retrieveColorOrUnspecified
import org.jetbrains.jewel.ui.component.styling.SliderColors
import org.jetbrains.jewel.ui.component.styling.SliderMetrics
import org.jetbrains.jewel.ui.component.styling.SliderStyle

internal fun readSliderStyle(dark: Boolean): SliderStyle {
    // There are no values for sliders in IntUi, so we're essentially reusing the
    // standalone colors logic, reading the palette values (and falling back to
    // hardcoded defaults).
    val colors = if (dark) SliderColors.dark() else SliderColors.light()
    return SliderStyle(colors, SliderMetrics.defaults(), CircleShape)
}

public fun SliderColors.Companion.light(
    track: Color = retrieveColorOrUnspecified("ColorPalette.Gray10").takeOrElse { Color(0xFFD3D5DB) },
    trackFilled: Color = retrieveColorOrUnspecified("ColorPalette.Blue6").takeOrElse { Color(0xFF588CF3) },
    trackDisabled: Color = retrieveColorOrUnspecified("ColorPalette.Gray12").takeOrElse { Color(0xFFEBECF0) },
    trackFilledDisabled: Color = retrieveColorOrUnspecified("ColorPalette.Gray11").takeOrElse { Color(0xFFDFE1E5) },
    stepMarker: Color = track,
    thumbFill: Color = retrieveColorOrUnspecified("ColorPalette.Gray14").takeOrElse { Color(0xFFFFFFFF) },
    thumbFillDisabled: Color = thumbFill,
    thumbFillFocused: Color = thumbFill,
    thumbFillPressed: Color = thumbFill,
    thumbFillHovered: Color = thumbFill,
    thumbBorder: Color = retrieveColorOrUnspecified("ColorPalette.Gray8").takeOrElse { Color(0xFFA8ADBD) },
    thumbBorderFocused: Color = retrieveColorOrUnspecified("ColorPalette.Blue4").takeOrElse { Color(0xFF3574F0) },
    thumbBorderDisabled: Color = retrieveColorOrUnspecified("ColorPalette.Gray11").takeOrElse { Color(0xFFDFE1E5) },
    thumbBorderPressed: Color = retrieveColorOrUnspecified("ColorPalette.Gray7").takeOrElse { Color(0xFF818594) },
    thumbBorderHovered: Color = retrieveColorOrUnspecified("ColorPalette.Gray9").takeOrElse { Color(0xFFC9CCD6) },
): SliderColors =
    SliderColors(
        track,
        trackFilled,
        trackDisabled,
        trackFilledDisabled,
        stepMarker,
        thumbFill,
        thumbFillDisabled,
        thumbFillFocused,
        thumbFillPressed,
        thumbFillHovered,
        thumbBorder,
        thumbBorderFocused,
        thumbBorderDisabled,
        thumbBorderPressed,
        thumbBorderHovered,
    )

public fun SliderColors.Companion.dark(
    track: Color = retrieveColorOrUnspecified("ColorPalette.Gray4").takeOrElse { Color(0xFF43454A) },
    trackFilled: Color = retrieveColorOrUnspecified("ColorPalette.Blue7").takeOrElse { Color(0xFF467FF2) },
    trackDisabled: Color = retrieveColorOrUnspecified("ColorPalette.Gray3").takeOrElse { Color(0xFF393B40) },
    trackFilledDisabled: Color = retrieveColorOrUnspecified("ColorPalette.Gray4").takeOrElse { Color(0xFF43454A) },
    stepMarker: Color = track,
    thumbFill: Color = retrieveColorOrUnspecified("ColorPalette.Gray2").takeOrElse { Color(0xFF2B2D30) },
    thumbFillDisabled: Color = retrieveColorOrUnspecified("ColorPalette.Gray3").takeOrElse { Color(0xFF393B40) },
    thumbFillFocused: Color = thumbFill,
    thumbFillPressed: Color = thumbFill,
    thumbFillHovered: Color = thumbFill,
    thumbBorder: Color = retrieveColorOrUnspecified("ColorPalette.Gray7").takeOrElse { Color(0xFF6F737A) },
    thumbBorderFocused: Color = retrieveColorOrUnspecified("ColorPalette.Blue6").takeOrElse { Color(0xFF3574F0) },
    thumbBorderDisabled: Color = retrieveColorOrUnspecified("ColorPalette.Gray5").takeOrElse { Color(0xFF4E5157) },
    thumbBorderPressed: Color = retrieveColorOrUnspecified("ColorPalette.Gray8").takeOrElse { Color(0xFF868A91) },
    thumbBorderHovered: Color = retrieveColorOrUnspecified("ColorPalette.Gray9").takeOrElse { Color(0xFF9DA0A8) },
): SliderColors =
    SliderColors(
        track,
        trackFilled,
        trackDisabled,
        trackFilledDisabled,
        stepMarker,
        thumbFill,
        thumbFillDisabled,
        thumbFillFocused,
        thumbFillPressed,
        thumbFillHovered,
        thumbBorder,
        thumbBorderFocused,
        thumbBorderDisabled,
        thumbBorderPressed,
        thumbBorderHovered,
    )

public fun SliderMetrics.Companion.defaults(
    trackHeight: Dp = 4.dp,
    thumbSize: DpSize = DpSize(14.dp, 14.dp),
    thumbBorderWidth: Dp = 1.dp,
    stepLineHeight: Dp = 8.dp,
    stepLineWidth: Dp = 1.dp,
    trackToStepSpacing: Dp = thumbSize.height / 2 + 4.dp,
): SliderMetrics =
    SliderMetrics(trackHeight, thumbSize, thumbBorderWidth, stepLineHeight, stepLineWidth, trackToStepSpacing)
