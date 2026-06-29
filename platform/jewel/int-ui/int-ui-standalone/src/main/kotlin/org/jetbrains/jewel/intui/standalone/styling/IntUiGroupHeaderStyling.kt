package org.jetbrains.jewel.intui.standalone.styling

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.jetbrains.jewel.intui.core.theme.IntUiDarkTheme
import org.jetbrains.jewel.intui.core.theme.IntUiLightTheme
import org.jetbrains.jewel.ui.component.styling.GroupHeaderColors
import org.jetbrains.jewel.ui.component.styling.GroupHeaderMetrics
import org.jetbrains.jewel.ui.component.styling.GroupHeaderStyle

/** Creates an Int UI light [GroupHeaderStyle] with the provided parameters. */
public fun GroupHeaderStyle.Companion.light(
    colors: GroupHeaderColors = GroupHeaderColors.light(),
    metrics: GroupHeaderMetrics = GroupHeaderMetrics.defaults(),
): GroupHeaderStyle = GroupHeaderStyle(colors, metrics)

/** Creates an Int UI dark [GroupHeaderStyle] with the provided parameters. */
public fun GroupHeaderStyle.Companion.dark(
    colors: GroupHeaderColors = GroupHeaderColors.dark(),
    metrics: GroupHeaderMetrics = GroupHeaderMetrics.defaults(),
): GroupHeaderStyle = GroupHeaderStyle(colors, metrics)

/** Creates an Int UI light [GroupHeaderColors] with the provided parameters. */
public fun GroupHeaderColors.Companion.light(divider: Color = IntUiLightTheme.colors.gray(12)): GroupHeaderColors =
    GroupHeaderColors(divider)

/** Creates an Int UI dark [GroupHeaderColors] with the provided parameters. */
public fun GroupHeaderColors.Companion.dark(divider: Color = IntUiDarkTheme.colors.gray(3)): GroupHeaderColors =
    GroupHeaderColors(divider)

/** Creates an Int UI default [GroupHeaderMetrics] with the provided parameters. */
public fun GroupHeaderMetrics.Companion.defaults(dividerThickness: Dp = 1.dp, indent: Dp = 8.dp): GroupHeaderMetrics =
    GroupHeaderMetrics(dividerThickness, indent)
