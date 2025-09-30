package org.jetbrains.jewel.intui.standalone.styling

import androidx.compose.ui.graphics.Color
import kotlin.time.Duration.Companion.milliseconds
import org.jetbrains.jewel.intui.core.theme.IntUiDarkTheme
import org.jetbrains.jewel.intui.core.theme.IntUiLightTheme
import org.jetbrains.jewel.ui.component.styling.TooltipAutoHideBehavior
import org.jetbrains.jewel.ui.component.styling.TooltipColors
import org.jetbrains.jewel.ui.component.styling.TooltipMetrics
import org.jetbrains.jewel.ui.component.styling.TooltipStyle

@Deprecated("Please, use the overload with [TooltipAutoHideBehavior].")
public fun TooltipStyle.Companion.light(
    intUiTooltipColors: TooltipColors = TooltipColors.light(),
    intUiTooltipMetrics: TooltipMetrics = TooltipMetrics.defaults(regularDisappearDelay = 10000.milliseconds),
): TooltipStyle =
    TooltipStyle(
        colors = intUiTooltipColors,
        metrics = intUiTooltipMetrics,
        autoHideBehavior = TooltipAutoHideBehavior.Normal,
    )

public fun TooltipStyle.Companion.light(
    intUiTooltipColors: TooltipColors = TooltipColors.light(),
    intUiTooltipMetrics: TooltipMetrics = TooltipMetrics.defaults(regularDisappearDelay = 10000.milliseconds),
    autoHideBehavior: TooltipAutoHideBehavior = TooltipAutoHideBehavior.Normal,
): TooltipStyle =
    TooltipStyle(colors = intUiTooltipColors, metrics = intUiTooltipMetrics, autoHideBehavior = autoHideBehavior)

@Deprecated("Please, use the overload with [TooltipAutoHideBehavior].")
public fun TooltipStyle.Companion.dark(
    intUiTooltipColors: TooltipColors = TooltipColors.dark(),
    intUiTooltipMetrics: TooltipMetrics = TooltipMetrics.defaults(regularDisappearDelay = 10000.milliseconds),
): TooltipStyle =
    TooltipStyle(
        colors = intUiTooltipColors,
        metrics = intUiTooltipMetrics,
        autoHideBehavior = TooltipAutoHideBehavior.Normal,
    )

public fun TooltipStyle.Companion.dark(
    intUiTooltipColors: TooltipColors = TooltipColors.dark(),
    intUiTooltipMetrics: TooltipMetrics = TooltipMetrics.defaults(regularDisappearDelay = 10000.milliseconds),
    autoHideBehavior: TooltipAutoHideBehavior = TooltipAutoHideBehavior.Normal,
): TooltipStyle =
    TooltipStyle(colors = intUiTooltipColors, metrics = intUiTooltipMetrics, autoHideBehavior = autoHideBehavior)

public fun TooltipColors.Companion.light(
    backgroundColor: Color = IntUiLightTheme.colors.gray(2),
    contentColor: Color = IntUiLightTheme.colors.gray(12),
    borderColor: Color = backgroundColor,
    shadow: Color = Color(0x78919191), // Not a palette color
): TooltipColors = TooltipColors(backgroundColor, contentColor, borderColor, shadow)

public fun TooltipColors.Companion.dark(
    backgroundColor: Color = IntUiDarkTheme.colors.gray(2),
    contentColor: Color = IntUiDarkTheme.colors.gray(12),
    borderColor: Color = IntUiDarkTheme.colors.gray(3),
    shadow: Color = Color(0x66000000), // Not a palette color
): TooltipColors = TooltipColors(backgroundColor, contentColor, borderColor, shadow)
