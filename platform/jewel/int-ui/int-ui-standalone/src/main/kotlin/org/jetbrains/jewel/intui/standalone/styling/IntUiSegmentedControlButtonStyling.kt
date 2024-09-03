package org.jetbrains.jewel.intui.standalone.styling

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import org.jetbrains.jewel.intui.core.theme.IntUiDarkTheme
import org.jetbrains.jewel.intui.core.theme.IntUiLightTheme
import org.jetbrains.jewel.ui.component.styling.SegmentedControlButtonColors
import org.jetbrains.jewel.ui.component.styling.SegmentedControlButtonMetrics
import org.jetbrains.jewel.ui.component.styling.SegmentedControlButtonStyle

@Composable
public fun SegmentedControlButtonStyle.Companion.light(
    colors: SegmentedControlButtonColors = SegmentedControlButtonColors.light(),
    metrics: SegmentedControlButtonMetrics = SegmentedControlButtonMetrics.defaults(),
): SegmentedControlButtonStyle = SegmentedControlButtonStyle(colors, metrics)

@Composable
public fun SegmentedControlButtonStyle.Companion.dark(
    colors: SegmentedControlButtonColors = SegmentedControlButtonColors.dark(),
    metrics: SegmentedControlButtonMetrics = SegmentedControlButtonMetrics.defaults(),
): SegmentedControlButtonStyle = SegmentedControlButtonStyle(colors, metrics)

@Composable
public fun SegmentedControlButtonColors.Companion.light(
    background: Brush = SolidColor(Color.Transparent),
    backgroundPressed: Brush = SolidColor(IntUiLightTheme.colors.gray(14)),
    backgroundHovered: Brush = SolidColor(IntUiLightTheme.colors.gray(1).copy(alpha = .07f)),
    backgroundSelected: Brush = SolidColor(IntUiLightTheme.colors.gray(14)),
    backgroundSelectedFocused: Brush = SolidColor(IntUiLightTheme.colors.blue(11)),
    content: Color = IntUiLightTheme.colors.gray(1),
    contentDisabled: Color = IntUiLightTheme.colors.gray(8),
    border: Brush = SolidColor(Color.Transparent),
    borderSelected: Brush = SolidColor(IntUiLightTheme.colors.gray(8)),
    borderSelectedDisabled: Brush = SolidColor(IntUiLightTheme.colors.gray(9)),
    borderSelectedFocused: Brush = SolidColor(IntUiLightTheme.colors.gray(14)),
): SegmentedControlButtonColors =
    SegmentedControlButtonColors(
        background = background,
        backgroundPressed = backgroundPressed,
        backgroundHovered = backgroundHovered,
        backgroundSelected = backgroundSelected,
        backgroundSelectedFocused = backgroundSelectedFocused,
        content = content,
        contentDisabled = contentDisabled,
        border = border,
        borderSelected = borderSelected,
        borderSelectedDisabled = borderSelectedDisabled,
        borderSelectedFocused = borderSelectedFocused,
    )

@Composable
public fun SegmentedControlButtonColors.Companion.dark(
    background: Brush = SolidColor(Color.Transparent),
    backgroundPressed: Brush = SolidColor(IntUiDarkTheme.colors.gray(3)),
    backgroundHovered: Brush = SolidColor(IntUiDarkTheme.colors.gray(14).copy(alpha = .09f)),
    backgroundSelected: Brush = SolidColor(IntUiDarkTheme.colors.gray(3)),
    backgroundSelectedFocused: Brush = SolidColor(IntUiDarkTheme.colors.blue(3)),
    content: Color = IntUiDarkTheme.colors.gray(12),
    contentDisabled: Color = IntUiDarkTheme.colors.gray(8),
    border: Brush = SolidColor(Color.Transparent),
    borderSelected: Brush = SolidColor(IntUiDarkTheme.colors.gray(7)),
    borderSelectedDisabled: Brush = SolidColor(IntUiDarkTheme.colors.gray(4)),
    borderSelectedFocused: Brush = SolidColor(IntUiDarkTheme.colors.gray(2)),
): SegmentedControlButtonColors =
    SegmentedControlButtonColors(
        background = background,
        backgroundPressed = backgroundPressed,
        backgroundHovered = backgroundHovered,
        backgroundSelected = backgroundSelected,
        backgroundSelectedFocused = backgroundSelectedFocused,
        content = content,
        contentDisabled = contentDisabled,
        border = border,
        borderSelected = borderSelected,
        borderSelectedDisabled = borderSelectedDisabled,
        borderSelectedFocused = borderSelectedFocused,
    )

public fun SegmentedControlButtonMetrics.Companion.defaults(
    cornerSize: CornerSize = CornerSize(3.dp),
    segmentedButtonPadding: PaddingValues = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
    minSize: DpSize = DpSize(width = Dp.Unspecified, height = 24.dp - 2.dp),
    borderWidth: Dp = 1.dp,
): SegmentedControlButtonMetrics =
    SegmentedControlButtonMetrics(cornerSize, segmentedButtonPadding, minSize, borderWidth)
