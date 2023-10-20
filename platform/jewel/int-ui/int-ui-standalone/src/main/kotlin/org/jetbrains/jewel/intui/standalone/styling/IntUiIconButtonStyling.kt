package org.jetbrains.jewel.intui.standalone.styling

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import org.jetbrains.jewel.intui.core.theme.IntUiDarkTheme
import org.jetbrains.jewel.intui.core.theme.IntUiLightTheme
import org.jetbrains.jewel.styling.IconButtonColors
import org.jetbrains.jewel.styling.IconButtonMetrics
import org.jetbrains.jewel.styling.IconButtonStyle

@Composable
fun IconButtonStyle.Companion.light(
    colors: IconButtonColors = IconButtonColors.light(),
    metrics: IconButtonMetrics = IconButtonMetrics.defaults(),
) = IconButtonStyle(colors, metrics)

@Composable
fun IconButtonStyle.Companion.dark(
    colors: IconButtonColors = IconButtonColors.dark(),
    metrics: IconButtonMetrics = IconButtonMetrics.defaults(),
) = IconButtonStyle(colors, metrics)

@Composable
fun IconButtonColors.Companion.light(
    background: Color = Color.Unspecified,
    backgroundDisabled: Color = background,
    backgroundFocused: Color = background,
    backgroundPressed: Color = IntUiLightTheme.colors.grey(11),
    backgroundHovered: Color = IntUiLightTheme.colors.grey(12),
    border: Color = background,
    borderDisabled: Color = border,
    borderFocused: Color = IntUiLightTheme.colors.blue(5),
    borderPressed: Color = backgroundPressed,
    borderHovered: Color = backgroundHovered,
) =
    IconButtonColors(
        background,
        backgroundDisabled,
        backgroundFocused,
        backgroundPressed,
        backgroundHovered,
        border,
        borderDisabled,
        borderFocused,
        borderPressed,
        borderHovered,
    )

@Composable
fun IconButtonColors.Companion.dark(
    background: Color = Color.Unspecified,
    backgroundDisabled: Color = background,
    backgroundFocused: Color = background,
    backgroundPressed: Color = IntUiDarkTheme.colors.grey(5),
    backgroundHovered: Color = IntUiDarkTheme.colors.grey(3),
    border: Color = background,
    borderDisabled: Color = border,
    borderFocused: Color = IntUiDarkTheme.colors.blue(6),
    borderPressed: Color = backgroundPressed,
    borderHovered: Color = backgroundHovered,
) =
    IconButtonColors(
        background,
        backgroundDisabled,
        backgroundFocused,
        backgroundPressed,
        backgroundHovered,
        border,
        borderDisabled,
        borderFocused,
        borderPressed,
        borderHovered,
    )

fun IconButtonMetrics.Companion.defaults(
    cornerSize: CornerSize = CornerSize(4.dp),
    borderWidth: Dp = 1.dp,
    padding: PaddingValues = PaddingValues(0.dp),
    minSize: DpSize = DpSize(16.dp, 16.dp),
) = IconButtonMetrics(cornerSize, borderWidth, padding, minSize)
