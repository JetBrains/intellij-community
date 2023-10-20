package org.jetbrains.jewel.intui.standalone.styling

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.jetbrains.jewel.intui.core.theme.IntUiDarkTheme
import org.jetbrains.jewel.intui.core.theme.IntUiLightTheme
import org.jetbrains.jewel.styling.GroupHeaderColors
import org.jetbrains.jewel.styling.GroupHeaderMetrics
import org.jetbrains.jewel.styling.GroupHeaderStyle

@Composable
fun GroupHeaderStyle.Companion.light(
    colors: GroupHeaderColors = GroupHeaderColors.light(),
    metrics: GroupHeaderMetrics = GroupHeaderMetrics.defaults(),
) = GroupHeaderStyle(colors, metrics)

@Composable
fun GroupHeaderStyle.Companion.dark(
    colors: GroupHeaderColors = GroupHeaderColors.dark(),
    metrics: GroupHeaderMetrics = GroupHeaderMetrics.defaults(),
) = GroupHeaderStyle(colors, metrics)

@Composable
fun GroupHeaderColors.Companion.light(
    divider: Color = IntUiLightTheme.colors.grey(12),
) = GroupHeaderColors(divider)

@Composable
fun GroupHeaderColors.Companion.dark(
    divider: Color = IntUiDarkTheme.colors.grey(3),
) = GroupHeaderColors(divider)

fun GroupHeaderMetrics.Companion.defaults(
    dividerThickness: Dp = 1.dp,
    indent: Dp = 8.dp,
) = GroupHeaderMetrics(dividerThickness, indent)
