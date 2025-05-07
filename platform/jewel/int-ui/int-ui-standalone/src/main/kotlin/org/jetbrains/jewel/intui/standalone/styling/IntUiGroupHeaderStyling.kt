package org.jetbrains.jewel.intui.standalone.styling

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.jetbrains.jewel.intui.core.theme.IntUiDarkTheme
import org.jetbrains.jewel.intui.core.theme.IntUiLightTheme
import org.jetbrains.jewel.ui.component.styling.GroupHeaderColors
import org.jetbrains.jewel.ui.component.styling.GroupHeaderMetrics
import org.jetbrains.jewel.ui.component.styling.GroupHeaderStyle

public fun GroupHeaderStyle.Companion.light(
    colors: GroupHeaderColors = GroupHeaderColors.light(),
    metrics: GroupHeaderMetrics = GroupHeaderMetrics.defaults(),
): GroupHeaderStyle = GroupHeaderStyle(colors, metrics)

public fun GroupHeaderStyle.Companion.dark(
    colors: GroupHeaderColors = GroupHeaderColors.dark(),
    metrics: GroupHeaderMetrics = GroupHeaderMetrics.defaults(),
): GroupHeaderStyle = GroupHeaderStyle(colors, metrics)

public fun GroupHeaderColors.Companion.light(divider: Color = IntUiLightTheme.colors.gray(12)): GroupHeaderColors =
    GroupHeaderColors(divider)

public fun GroupHeaderColors.Companion.dark(divider: Color = IntUiDarkTheme.colors.gray(3)): GroupHeaderColors =
    GroupHeaderColors(divider)

public fun GroupHeaderMetrics.Companion.defaults(dividerThickness: Dp = 1.dp, indent: Dp = 8.dp): GroupHeaderMetrics =
    GroupHeaderMetrics(dividerThickness, indent)
