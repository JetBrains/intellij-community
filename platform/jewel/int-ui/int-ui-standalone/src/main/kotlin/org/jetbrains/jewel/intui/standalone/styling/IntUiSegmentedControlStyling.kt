package org.jetbrains.jewel.intui.standalone.styling

import androidx.compose.foundation.shape.CornerSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.jetbrains.jewel.intui.core.theme.IntUiDarkTheme
import org.jetbrains.jewel.intui.core.theme.IntUiLightTheme
import org.jetbrains.jewel.ui.component.styling.SegmentedControlColors
import org.jetbrains.jewel.ui.component.styling.SegmentedControlMetrics
import org.jetbrains.jewel.ui.component.styling.SegmentedControlStyle

@Composable
public fun SegmentedControlStyle.Companion.light(
    colors: SegmentedControlColors = SegmentedControlColors.light(),
    metrics: SegmentedControlMetrics = SegmentedControlMetrics.defaults(),
): SegmentedControlStyle = SegmentedControlStyle(colors, metrics)

@Composable
public fun SegmentedControlStyle.Companion.dark(
    colors: SegmentedControlColors = SegmentedControlColors.dark(),
    metrics: SegmentedControlMetrics = SegmentedControlMetrics.defaults(),
): SegmentedControlStyle = SegmentedControlStyle(colors, metrics)

@Composable
public fun SegmentedControlColors.Companion.light(
    border: Brush = SolidColor(IntUiLightTheme.colors.gray(9)),
    borderDisabled: Brush = SolidColor(IntUiLightTheme.colors.gray(11)),
    borderFocused: Brush = SolidColor(Color.Transparent),
    borderPressed: Brush = border,
    borderHovered: Brush = border,
): SegmentedControlColors =
    SegmentedControlColors(
        border = border,
        borderDisabled = borderDisabled,
        borderPressed = borderPressed,
        borderHovered = borderHovered,
        borderFocused = borderFocused,
    )

@Composable
public fun SegmentedControlColors.Companion.dark(
    border: Brush = SolidColor(IntUiDarkTheme.colors.gray(5)),
    borderDisabled: Brush = SolidColor(IntUiDarkTheme.colors.gray(4)),
    borderFocused: Brush = SolidColor(IntUiDarkTheme.colors.gray(2)),
    borderPressed: Brush = border,
    borderHovered: Brush = border,
): SegmentedControlColors =
    SegmentedControlColors(
        border = border,
        borderDisabled = borderDisabled,
        borderPressed = borderPressed,
        borderHovered = borderHovered,
        borderFocused = borderFocused,
    )

public fun SegmentedControlMetrics.Companion.defaults(
    cornerSize: CornerSize = CornerSize(3.dp),
    borderWidth: Dp = 1.dp,
): SegmentedControlMetrics = SegmentedControlMetrics(cornerSize, borderWidth)
