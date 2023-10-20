package org.jetbrains.jewel.intui.standalone.styling

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import org.jetbrains.jewel.intui.core.styling.defaults
import org.jetbrains.jewel.intui.core.theme.IntUiDarkTheme
import org.jetbrains.jewel.intui.core.theme.IntUiLightTheme
import org.jetbrains.jewel.styling.TooltipColors
import org.jetbrains.jewel.styling.TooltipMetrics
import org.jetbrains.jewel.styling.TooltipStyle

@Composable
fun TooltipStyle.Companion.light(
    intUiTooltipColors: TooltipColors = TooltipColors.light(),
    intUiTooltipMetrics: TooltipMetrics = TooltipMetrics.defaults(),
): TooltipStyle = TooltipStyle(
    colors = intUiTooltipColors,
    metrics = intUiTooltipMetrics,
)

@Composable
fun TooltipStyle.Companion.dark(
    intUiTooltipColors: TooltipColors = TooltipColors.dark(),
    intUiTooltipMetrics: TooltipMetrics = TooltipMetrics.defaults(),
): TooltipStyle = TooltipStyle(
    colors = intUiTooltipColors,
    metrics = intUiTooltipMetrics,
)

@Composable
fun TooltipColors.Companion.light(
    backgroundColor: Color = IntUiLightTheme.colors.grey(2),
    contentColor: Color = IntUiLightTheme.colors.grey(12),
    borderColor: Color = backgroundColor,
    shadow: Color = Color(0x78919191), // Not a palette color
) = TooltipColors(backgroundColor, contentColor, borderColor, shadow)

@Composable
fun TooltipColors.Companion.dark(
    backgroundColor: Color = IntUiDarkTheme.colors.grey(2),
    contentColor: Color = IntUiDarkTheme.colors.grey(12),
    borderColor: Color = IntUiDarkTheme.colors.grey(3),
    shadow: Color = Color(0x66000000), // Not a palette color
) = TooltipColors(backgroundColor, contentColor, borderColor, shadow)
