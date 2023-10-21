package org.jetbrains.jewel.intui.window.styling

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.jetbrains.jewel.window.styling.DecoratedWindowColors
import org.jetbrains.jewel.window.styling.DecoratedWindowMetrics
import org.jetbrains.jewel.window.styling.DecoratedWindowStyle

fun DecoratedWindowStyle.Companion.light(
    colors: DecoratedWindowColors = DecoratedWindowColors.light(),
    metrics: DecoratedWindowMetrics = DecoratedWindowMetrics.defaults(),
) = DecoratedWindowStyle(colors, metrics)

fun DecoratedWindowStyle.Companion.dark(
    colors: DecoratedWindowColors = DecoratedWindowColors.dark(),
    metrics: DecoratedWindowMetrics = DecoratedWindowMetrics.defaults(),
) = DecoratedWindowStyle(colors, metrics)

fun DecoratedWindowColors.Companion.light(
    // from Window.undecorated.border
    borderColor: Color = Color(0xFF5A5D6B),
    inactiveBorderColor: Color = borderColor,
) = DecoratedWindowColors(borderColor, inactiveBorderColor)

fun DecoratedWindowColors.Companion.dark(
    // from Window.undecorated.border
    borderColor: Color = Color(0xFF5A5D63),
    inactiveBorderColor: Color = borderColor,
) = DecoratedWindowColors(borderColor, inactiveBorderColor)

fun DecoratedWindowMetrics.Companion.defaults(
    borderWidth: Dp = 1.dp,
) = DecoratedWindowMetrics(borderWidth)
