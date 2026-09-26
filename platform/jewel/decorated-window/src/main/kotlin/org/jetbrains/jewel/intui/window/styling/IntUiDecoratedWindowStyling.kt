package org.jetbrains.jewel.intui.window.styling

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.jetbrains.jewel.window.styling.DecoratedWindowColors
import org.jetbrains.jewel.window.styling.DecoratedWindowMetrics
import org.jetbrains.jewel.window.styling.DecoratedWindowStyle

/**
 * Creates an Int UI light [DecoratedWindowStyle].
 *
 * @param colors The [DecoratedWindowColors] to use. Defaults to [DecoratedWindowColors.light].
 * @param metrics The [DecoratedWindowMetrics] to use. Defaults to [DecoratedWindowMetrics.defaults].
 */
public fun DecoratedWindowStyle.Companion.light(
    colors: DecoratedWindowColors = DecoratedWindowColors.light(),
    metrics: DecoratedWindowMetrics = DecoratedWindowMetrics.defaults(),
): DecoratedWindowStyle = DecoratedWindowStyle(colors, metrics)

/**
 * Creates an Int UI dark [DecoratedWindowStyle].
 *
 * @param colors The [DecoratedWindowColors] to use. Defaults to [DecoratedWindowColors.dark].
 * @param metrics The [DecoratedWindowMetrics] to use. Defaults to [DecoratedWindowMetrics.defaults].
 */
public fun DecoratedWindowStyle.Companion.dark(
    colors: DecoratedWindowColors = DecoratedWindowColors.dark(),
    metrics: DecoratedWindowMetrics = DecoratedWindowMetrics.defaults(),
): DecoratedWindowStyle = DecoratedWindowStyle(colors, metrics)

/**
 * Creates Int UI light [DecoratedWindowColors] using the standard undecorated window border color.
 *
 * @param borderColor The active window border color. Defaults to `#5A5D6B`.
 * @param inactiveBorderColor The inactive window border color. Defaults to [borderColor].
 */
public fun DecoratedWindowColors.Companion.light(
    // from Window.undecorated.border
    borderColor: Color = Color(0xFF5A5D6B),
    inactiveBorderColor: Color = borderColor,
): DecoratedWindowColors = DecoratedWindowColors(borderColor, inactiveBorderColor)

/**
 * Creates Int UI dark [DecoratedWindowColors] using the standard undecorated window border color.
 *
 * @param borderColor The active window border color. Defaults to `#5A5D63`.
 * @param inactiveBorderColor The inactive window border color. Defaults to [borderColor].
 */
public fun DecoratedWindowColors.Companion.dark(
    // from Window.undecorated.border
    borderColor: Color = Color(0xFF5A5D63),
    inactiveBorderColor: Color = borderColor,
): DecoratedWindowColors = DecoratedWindowColors(borderColor, inactiveBorderColor)

/**
 * Creates default [DecoratedWindowMetrics] with a 1dp border width.
 *
 * @param borderWidth The width of the border drawn around the undecorated window on Linux.
 */
public fun DecoratedWindowMetrics.Companion.defaults(borderWidth: Dp = 1.dp): DecoratedWindowMetrics =
    DecoratedWindowMetrics(borderWidth)
