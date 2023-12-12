package org.jetbrains.jewel.intui.standalone.styling

import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import org.jetbrains.jewel.intui.core.theme.IntUiDarkTheme
import org.jetbrains.jewel.intui.core.theme.IntUiLightTheme
import org.jetbrains.jewel.ui.component.styling.SliderColors
import org.jetbrains.jewel.ui.component.styling.SliderMetrics
import org.jetbrains.jewel.ui.component.styling.SliderStyle

@Composable
public fun SliderStyle.Companion.light(
    colors: SliderColors = SliderColors.light(),
    metrics: SliderMetrics = SliderMetrics.defaults(),
    thumbShape: Shape = CircleShape,
): SliderStyle = SliderStyle(colors, metrics, thumbShape)

@Composable
public fun SliderStyle.Companion.dark(
    colors: SliderColors = SliderColors.dark(),
    metrics: SliderMetrics = SliderMetrics.defaults(),
    thumbShape: Shape = CircleShape,
): SliderStyle = SliderStyle(colors, metrics, thumbShape)

@Composable
public fun SliderColors.Companion.light(
    track: Color = IntUiLightTheme.colors.grey(10),
    trackFilled: Color = IntUiLightTheme.colors.blue(6),
    trackDisabled: Color = IntUiLightTheme.colors.grey(12),
    trackFilledDisabled: Color = IntUiLightTheme.colors.grey(11),
    stepMarker: Color = track,
    thumbFill: Color = IntUiLightTheme.colors.grey(14),
    thumbFillDisabled: Color = thumbFill,
    thumbFillFocused: Color = thumbFill,
    thumbFillPressed: Color = thumbFill,
    thumbFillHovered: Color = thumbFill,
    thumbBorder: Color = IntUiLightTheme.colors.grey(8),
    thumbBorderFocused: Color = IntUiLightTheme.colors.blue(4),
    thumbBorderDisabled: Color = IntUiLightTheme.colors.grey(11),
    thumbBorderPressed: Color = IntUiLightTheme.colors.grey(7),
    thumbBorderHovered: Color = IntUiLightTheme.colors.grey(9),
): SliderColors = SliderColors(
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

@Composable
public fun SliderColors.Companion.dark(
    track: Color = IntUiDarkTheme.colors.grey(4),
    trackFilled: Color = IntUiDarkTheme.colors.blue(7),
    trackDisabled: Color = IntUiDarkTheme.colors.grey(3),
    trackFilledDisabled: Color = IntUiDarkTheme.colors.grey(4),
    stepMarker: Color = track,
    thumbFill: Color = IntUiDarkTheme.colors.grey(2),
    thumbFillDisabled: Color = IntUiDarkTheme.colors.grey(3),
    thumbFillFocused: Color = thumbFill,
    thumbFillPressed: Color = thumbFill,
    thumbFillHovered: Color = thumbFill,
    thumbBorder: Color = IntUiDarkTheme.colors.grey(7),
    thumbBorderFocused: Color = IntUiDarkTheme.colors.blue(6),
    thumbBorderDisabled: Color = IntUiDarkTheme.colors.grey(5),
    thumbBorderPressed: Color = IntUiDarkTheme.colors.grey(8),
    thumbBorderHovered: Color = IntUiDarkTheme.colors.grey(9),
): SliderColors = SliderColors(
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

@Composable
public fun SliderMetrics.Companion.defaults(
    trackHeight: Dp = 4.dp,
    thumbSize: DpSize = DpSize(14.dp, 14.dp),
    thumbBorderWidth: Dp = 1.dp,
    stepLineHeight: Dp = 8.dp,
    stepLineWidth: Dp = 1.dp,
    trackToStepSpacing: Dp = thumbSize.height / 2 + 4.dp,
): SliderMetrics =
    SliderMetrics(trackHeight, thumbSize, thumbBorderWidth, stepLineHeight, stepLineWidth, trackToStepSpacing)
