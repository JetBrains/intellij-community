package org.jetbrains.jewel.intui.standalone.styling

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.jetbrains.jewel.ui.component.styling.ScrollbarColors
import org.jetbrains.jewel.ui.component.styling.ScrollbarMetrics
import org.jetbrains.jewel.ui.component.styling.ScrollbarStyle
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

@Composable
public fun ScrollbarStyle.Companion.light(
    colors: ScrollbarColors = ScrollbarColors.light(),
    metrics: ScrollbarMetrics = ScrollbarMetrics.defaults(),
    hoverDuration: Duration = 300.milliseconds,
): ScrollbarStyle =
    ScrollbarStyle(colors, metrics, hoverDuration)

@Composable
public fun ScrollbarStyle.Companion.dark(
    colors: ScrollbarColors = ScrollbarColors.dark(),
    metrics: ScrollbarMetrics = ScrollbarMetrics.defaults(),
    hoverDuration: Duration = 300.milliseconds,
): ScrollbarStyle =
    ScrollbarStyle(colors, metrics, hoverDuration)

@Composable
public fun ScrollbarColors.Companion.light(
    thumbBackground: Color = Color(0x33000000),
    thumbBackgroundHovered: Color = Color(0x80000000),
): ScrollbarColors =
    ScrollbarColors(thumbBackground, thumbBackgroundHovered)

@Composable
public fun ScrollbarColors.Companion.dark(
    thumbBackground: Color = Color(0x59808080),
    thumbBackgroundHovered: Color = Color(0x8C808080),
): ScrollbarColors =
    ScrollbarColors(thumbBackground, thumbBackgroundHovered)

public fun ScrollbarMetrics.Companion.defaults(
    thumbCornerSize: CornerSize = CornerSize(100),
    thumbThickness: Dp = 8.dp,
    minThumbLength: Dp = 16.dp,
    trackPadding: PaddingValues = PaddingValues(start = 7.dp, end = 3.dp),
): ScrollbarMetrics =
    ScrollbarMetrics(thumbCornerSize, thumbThickness, minThumbLength, trackPadding)
