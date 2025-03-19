package org.jetbrains.jewel.intui.standalone.styling

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import org.jetbrains.jewel.intui.core.theme.IntUiDarkTheme
import org.jetbrains.jewel.intui.core.theme.IntUiLightTheme
import org.jetbrains.jewel.ui.component.styling.PopupContainerColors
import org.jetbrains.jewel.ui.component.styling.PopupContainerMetrics
import org.jetbrains.jewel.ui.component.styling.PopupContainerStyle

@Composable
public fun PopupContainerStyle.Companion.light(
    colors: PopupContainerColors = PopupContainerColors.light(),
    metrics: PopupContainerMetrics = PopupContainerMetrics.defaults(),
): PopupContainerStyle = PopupContainerStyle(isDark = false, colors, metrics)

@Composable
public fun PopupContainerStyle.Companion.dark(
    colors: PopupContainerColors = PopupContainerColors.dark(),
    metrics: PopupContainerMetrics = PopupContainerMetrics.defaults(),
): PopupContainerStyle = PopupContainerStyle(isDark = true, colors, metrics)

@Composable
public fun PopupContainerColors.Companion.light(
    background: Color = IntUiLightTheme.colors.gray(14),
    border: Color = IntUiLightTheme.colors.gray(9),
    shadow: Color = Color(0x78919191), // Not a palette color
): PopupContainerColors = PopupContainerColors(background = background, border = border, shadow = shadow)

@Composable
public fun PopupContainerColors.Companion.dark(
    background: Color = IntUiDarkTheme.colors.gray(2),
    border: Color = IntUiDarkTheme.colors.gray(3),
    shadow: Color = Color(0x66000000), // Not a palette color
): PopupContainerColors = PopupContainerColors(background = background, border = border, shadow = shadow)

public fun PopupContainerMetrics.Companion.defaults(
    cornerSize: CornerSize = CornerSize(8.dp),
    menuMargin: PaddingValues = PaddingValues(vertical = 6.dp),
    contentPadding: PaddingValues = PaddingValues(0.dp),
    offset: DpOffset = DpOffset(0.dp, 2.dp),
    shadowSize: Dp = 12.dp,
    borderWidth: Dp = 1.dp,
): PopupContainerMetrics =
    PopupContainerMetrics(cornerSize, menuMargin, contentPadding, offset, shadowSize, borderWidth)
