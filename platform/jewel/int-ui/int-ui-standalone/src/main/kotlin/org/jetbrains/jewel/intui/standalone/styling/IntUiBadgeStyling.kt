package org.jetbrains.jewel.intui.standalone.styling

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import org.jetbrains.jewel.intui.core.theme.IntUiDarkTheme
import org.jetbrains.jewel.intui.core.theme.IntUiLightTheme
import org.jetbrains.jewel.ui.component.styling.BadgeColors
import org.jetbrains.jewel.ui.component.styling.BadgeMetrics
import org.jetbrains.jewel.ui.component.styling.BadgeStyle

public fun BadgeStyle.Companion.light(
    colors: BadgeColors = BadgeColors.light(),
    metrics: BadgeMetrics = BadgeMetrics.defaults(),
): BadgeStyle = BadgeStyle(colors = colors, metrics = metrics)

public fun BadgeStyle.Companion.dark(
    colors: BadgeColors = BadgeColors.dark(),
    metrics: BadgeMetrics = BadgeMetrics.defaults(),
): BadgeStyle = BadgeStyle(colors = colors, metrics = metrics)

public fun BadgeColors.Companion.light(
    background: Brush = SolidColor(IntUiLightTheme.colors.blue(4)),
    backgroundDisabled: Brush = SolidColor(IntUiLightTheme.colors.gray(12)),
    backgroundFocused: Brush = SolidColor(IntUiLightTheme.colors.blue(5)),
    backgroundPressed: Brush = SolidColor(IntUiLightTheme.colors.blue(6)),
    backgroundHovered: Brush = SolidColor(IntUiLightTheme.colors.blue(5)),
    content: Color = IntUiLightTheme.colors.gray(14),
    contentDisabled: Color = IntUiLightTheme.colors.gray(8),
    contentFocused: Color = IntUiLightTheme.colors.gray(14),
    contentPressed: Color = IntUiLightTheme.colors.gray(14),
    contentHovered: Color = IntUiLightTheme.colors.gray(14),
): BadgeColors =
    BadgeColors(
        background = background,
        backgroundDisabled = backgroundDisabled,
        backgroundFocused = backgroundFocused,
        backgroundPressed = backgroundPressed,
        backgroundHovered = backgroundHovered,
        content = content,
        contentDisabled = contentDisabled,
        contentFocused = contentFocused,
        contentPressed = contentPressed,
        contentHovered = contentHovered,
    )

public fun BadgeColors.Companion.dark(
    background: Brush = SolidColor(IntUiDarkTheme.colors.blue(6)),
    backgroundDisabled: Brush = SolidColor(IntUiDarkTheme.colors.gray(3)),
    backgroundFocused: Brush = SolidColor(IntUiDarkTheme.colors.blue(5)),
    backgroundPressed: Brush = SolidColor(IntUiDarkTheme.colors.blue(7)),
    backgroundHovered: Brush = SolidColor(IntUiDarkTheme.colors.blue(5)),
    content: Color = IntUiDarkTheme.colors.gray(14),
    contentDisabled: Color = IntUiDarkTheme.colors.gray(8),
    contentFocused: Color = IntUiDarkTheme.colors.gray(14),
    contentPressed: Color = IntUiDarkTheme.colors.gray(14),
    contentHovered: Color = IntUiDarkTheme.colors.gray(14),
): BadgeColors =
    BadgeColors(
        background = background,
        backgroundDisabled = backgroundDisabled,
        backgroundFocused = backgroundFocused,
        backgroundPressed = backgroundPressed,
        backgroundHovered = backgroundHovered,
        content = content,
        contentDisabled = contentDisabled,
        contentFocused = contentFocused,
        contentPressed = contentPressed,
        contentHovered = contentHovered,
    )

public fun BadgeMetrics.Companion.defaults(
    cornerSize: CornerSize = CornerSize(0.dp),
    padding: PaddingValues = PaddingValues(horizontal = 6.dp, vertical = 2.dp),
    minSize: DpSize = DpSize(32.dp, 18.dp),
): BadgeMetrics = BadgeMetrics(cornerSize = cornerSize, padding = padding, minSize = minSize)
