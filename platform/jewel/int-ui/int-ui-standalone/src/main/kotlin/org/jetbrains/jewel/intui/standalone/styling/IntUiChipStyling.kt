package org.jetbrains.jewel.intui.standalone.styling

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.jetbrains.jewel.intui.core.theme.IntUiDarkTheme
import org.jetbrains.jewel.intui.core.theme.IntUiLightTheme
import org.jetbrains.jewel.ui.component.styling.ChipColors
import org.jetbrains.jewel.ui.component.styling.ChipMetrics
import org.jetbrains.jewel.ui.component.styling.ChipStyle

@Composable
public fun ChipStyle.Companion.light(
    colors: ChipColors = ChipColors.light(),
    metrics: ChipMetrics = ChipMetrics.defaults(),
): ChipStyle = ChipStyle(colors, metrics)

@Composable
public fun ChipStyle.Companion.dark(
    colors: ChipColors = ChipColors.dark(),
    metrics: ChipMetrics = ChipMetrics.defaults(),
): ChipStyle = ChipStyle(colors, metrics)

@Composable
public fun ChipColors.Companion.light(
    background: Brush = SolidColor(IntUiLightTheme.colors.grey(14)),
    backgroundDisabled: Brush = SolidColor(IntUiLightTheme.colors.grey(12)),
    backgroundFocused: Brush = background,
    backgroundPressed: Brush = SolidColor(IntUiLightTheme.colors.grey(13)),
    backgroundHovered: Brush = background,
    backgroundSelected: Brush = background,
    backgroundSelectedDisabled: Brush = backgroundDisabled,
    backgroundSelectedFocused: Brush = background,
    backgroundSelectedPressed: Brush = background,
    backgroundSelectedHovered: Brush = background,
    content: Color = IntUiLightTheme.colors.grey(1),
    contentDisabled: Color = IntUiLightTheme.colors.grey(8),
    contentFocused: Color = content,
    contentPressed: Color = content,
    contentHovered: Color = content,
    contentSelected: Color = content,
    contentSelectedDisabled: Color = contentDisabled,
    contentSelectedFocused: Color = content,
    contentSelectedPressed: Color = content,
    contentSelectedHovered: Color = content,
    border: Color = IntUiLightTheme.colors.grey(9),
    borderDisabled: Color = IntUiLightTheme.colors.grey(6),
    borderFocused: Color = IntUiLightTheme.colors.blue(4),
    borderPressed: Color = IntUiLightTheme.colors.grey(7),
    borderHovered: Color = IntUiLightTheme.colors.grey(8),
    borderSelected: Color = IntUiLightTheme.colors.blue(4),
    borderSelectedDisabled: Color = borderSelected,
    borderSelectedFocused: Color = borderSelected,
    borderSelectedPressed: Color = borderSelected,
    borderSelectedHovered: Color = borderSelected,
): ChipColors =
    ChipColors(
        background = background,
        backgroundDisabled = backgroundDisabled,
        backgroundFocused = backgroundFocused,
        backgroundPressed = backgroundPressed,
        backgroundHovered = backgroundHovered,
        backgroundSelected = backgroundSelected,
        backgroundSelectedDisabled = backgroundSelectedDisabled,
        backgroundSelectedPressed = backgroundSelectedPressed,
        backgroundSelectedFocused = backgroundSelectedFocused,
        backgroundSelectedHovered = backgroundSelectedHovered,
        content = content,
        contentDisabled = contentDisabled,
        contentFocused = contentFocused,
        contentPressed = contentPressed,
        contentHovered = contentHovered,
        contentSelected = contentSelected,
        contentSelectedDisabled = contentSelectedDisabled,
        contentSelectedPressed = contentSelectedPressed,
        contentSelectedFocused = contentSelectedFocused,
        contentSelectedHovered = contentSelectedHovered,
        border = border,
        borderDisabled = borderDisabled,
        borderFocused = borderFocused,
        borderPressed = borderPressed,
        borderHovered = borderHovered,
        borderSelected = borderSelected,
        borderSelectedDisabled = borderSelectedDisabled,
        borderSelectedPressed = borderSelectedPressed,
        borderSelectedFocused = borderSelectedFocused,
        borderSelectedHovered = borderSelectedHovered,
    )

@Composable
public fun ChipColors.Companion.dark(
    background: Brush = SolidColor(IntUiDarkTheme.colors.grey(2)),
    backgroundDisabled: Brush = SolidColor(IntUiDarkTheme.colors.grey(5)),
    backgroundFocused: Brush = background,
    backgroundPressed: Brush = background,
    backgroundHovered: Brush = background,
    backgroundSelected: Brush = background,
    backgroundSelectedDisabled: Brush = backgroundDisabled,
    backgroundSelectedFocused: Brush = background,
    backgroundSelectedPressed: Brush = background,
    backgroundSelectedHovered: Brush = background,
    content: Color = IntUiDarkTheme.colors.grey(12),
    contentDisabled: Color = IntUiDarkTheme.colors.grey(8),
    contentFocused: Color = content,
    contentPressed: Color = content,
    contentHovered: Color = content,
    contentSelected: Color = content,
    contentSelectedDisabled: Color = contentDisabled,
    contentSelectedFocused: Color = content,
    contentSelectedPressed: Color = content,
    contentSelectedHovered: Color = content,
    border: Color = IntUiDarkTheme.colors.grey(5),
    borderDisabled: Color = IntUiDarkTheme.colors.grey(6),
    borderFocused: Color = IntUiDarkTheme.colors.blue(6),
    borderPressed: Color = IntUiDarkTheme.colors.grey(7),
    borderHovered: Color = borderPressed,
    borderSelected: Color = IntUiDarkTheme.colors.blue(6),
    borderSelectedDisabled: Color = borderSelected,
    borderSelectedFocused: Color = borderSelected,
    borderSelectedPressed: Color = borderSelected,
    borderSelectedHovered: Color = borderSelected,
): ChipColors =
    ChipColors(
        background = background,
        backgroundDisabled = backgroundDisabled,
        backgroundFocused = backgroundFocused,
        backgroundPressed = backgroundPressed,
        backgroundHovered = backgroundHovered,
        backgroundSelected = backgroundSelected,
        backgroundSelectedDisabled = backgroundSelectedDisabled,
        backgroundSelectedPressed = backgroundSelectedPressed,
        backgroundSelectedFocused = backgroundSelectedFocused,
        backgroundSelectedHovered = backgroundSelectedHovered,
        content = content,
        contentDisabled = contentDisabled,
        contentFocused = contentFocused,
        contentPressed = contentPressed,
        contentHovered = contentHovered,
        contentSelected = contentSelected,
        contentSelectedDisabled = contentSelectedDisabled,
        contentSelectedPressed = contentSelectedPressed,
        contentSelectedFocused = contentSelectedFocused,
        contentSelectedHovered = contentSelectedHovered,
        border = border,
        borderDisabled = borderDisabled,
        borderFocused = borderFocused,
        borderPressed = borderPressed,
        borderHovered = borderHovered,
        borderSelected = borderSelected,
        borderSelectedDisabled = borderSelectedDisabled,
        borderSelectedPressed = borderSelectedPressed,
        borderSelectedFocused = borderSelectedFocused,
        borderSelectedHovered = borderSelectedHovered,
    )

public fun ChipMetrics.Companion.defaults(
    cornerSize: CornerSize = CornerSize(100),
    padding: PaddingValues = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
    borderWidth: Dp = 1.dp,
    borderWidthSelected: Dp = 2.dp,
): ChipMetrics =
    ChipMetrics(cornerSize, padding, borderWidth, borderWidthSelected)
