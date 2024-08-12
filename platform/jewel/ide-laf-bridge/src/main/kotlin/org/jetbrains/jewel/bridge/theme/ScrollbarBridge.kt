package org.jetbrains.jewel.bridge.theme

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.takeOrElse
import androidx.compose.ui.unit.dp
import org.jetbrains.jewel.bridge.MacScrollbarHelper
import org.jetbrains.jewel.bridge.retrieveColorOrUnspecified
import org.jetbrains.jewel.ui.component.styling.ScrollbarColors
import org.jetbrains.jewel.ui.component.styling.ScrollbarMetrics
import org.jetbrains.jewel.ui.component.styling.ScrollbarStyle
import org.jetbrains.jewel.ui.component.styling.ScrollbarVisibility
import org.jetbrains.jewel.ui.component.styling.TrackClickBehavior
import org.jetbrains.skiko.hostOs
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

internal fun readScrollbarStyle(isDark: Boolean): ScrollbarStyle =
    ScrollbarStyle(
        colors = readScrollbarColors(isDark),
        metrics = readScrollbarMetrics(),
        trackClickBehavior = readTrackClickBehavior(),
        scrollbarVisibility = readScrollbarVisibility(),
    )

private fun readScrollbarVisibility() =
    if (hostOs.isMacOS) {
        MacScrollbarHelper.scrollbarVisibility
    } else {
        ScrollbarVisibility.AlwaysVisible
    }

private fun readScrollbarColors(isDark: Boolean) =
    if (hostOs.isMacOS) {
        readScrollbarMacColors(isDark)
    } else {
        readScrollbarWinColors(isDark)
    }

private fun readTrackClickBehavior() =
    if (hostOs.isMacOS) {
        MacScrollbarHelper.trackClickBehavior
    } else {
        TrackClickBehavior.JumpToSpot
    }

private fun readScrollbarWinColors(isDark: Boolean): ScrollbarColors =
    ScrollbarColors(
        thumbBackground =
            readScrollBarColorForKey(
                isDark,
                "ScrollBar.thumbColor",
                0x33737373,
                0x47A6A6A6,
            ),
        thumbBackgroundHovered =
            readScrollBarColorForKey(
                isDark,
                "ScrollBar.hoverThumbColor",
                0x47737373,
                0x59A6A6A6,
            ),
        thumbBackgroundPressed =
            readScrollBarColorForKey(
                isDark,
                "ScrollBar.hoverThumbColor",
                0x47737373,
                0x59A6A6A6,
            ),
        thumbBorder =
            readScrollBarColorForKey(
                isDark,
                "ScrollBar.thumbBorderColor",
                0x33595959,
                0x47383838,
            ),
        thumbBorderHovered =
            readScrollBarColorForKey(
                isDark,
                "ScrollBar.hoverThumbBorderColor",
                0x47595959,
                0x59383838,
            ),
        thumbBorderPressed =
            readScrollBarColorForKey(
                isDark,
                "ScrollBar.hoverThumbBorderColor",
                0x47595959,
                0x59383838,
            ),
        trackBackground =
            readScrollBarColorForKey(
                isDark,
                "ScrollBar.Transparent.trackColor",
                0x00808080,
                0x00808080,
            ),
        trackBackgroundHovered =
            readScrollBarColorForKey(
                isDark,
                "ScrollBar.Transparent.hoverTrackColor",
                0x1A808080,
                0x1A808080,
            ),
    )

private fun readScrollbarMacColors(isDark: Boolean): ScrollbarColors =
    ScrollbarColors(
        thumbBackground =
            readScrollBarColorForKey(
                isDark,
                "ScrollBar.Mac.thumbColor",
                0x33000000,
                0x59808080,
            ),
        thumbBackgroundHovered =
            readScrollBarColorForKey(
                isDark,
                "ScrollBar.Mac.hoverThumbColor",
                0x80000000,
                0x8C808080,
            ),
        thumbBackgroundPressed =
            readScrollBarColorForKey(
                isDark,
                "ScrollBar.Mac.hoverThumbColor",
                0x80000000,
                0x8C808080,
            ),
        thumbBorder =
            readScrollBarColorForKey(
                isDark,
                "ScrollBar.Mac.thumbBorderColor",
                0x33000000,
                0x59262626,
            ),
        thumbBorderHovered =
            readScrollBarColorForKey(
                isDark,
                "ScrollBar.Mac.hoverThumbBorderColor",
                0x80000000,
                0x8C262626,
            ),
        thumbBorderPressed =
            readScrollBarColorForKey(
                isDark,
                "ScrollBar.Mac.hoverThumbBorderColor",
                0x80000000,
                0x8C262626,
            ),
        trackBackground =
            readScrollBarColorForKey(
                isDark,
                "ScrollBar.Mac.trackColor",
                0x00808080,
                0x00808080,
            ),
        trackBackgroundHovered =
            readScrollBarColorForKey(
                isDark,
                "ScrollBar.Mac.hoverTrackColor",
                0x00808080,
                0x00808080,
            ),
    )

private fun readScrollBarColorForKey(
    isDark: Boolean,
    colorKey: String,
    fallbackLight: Long,
    fallbackDark: Long,
) = retrieveColorOrUnspecified(colorKey)
    .takeOrElse { if (isDark) Color(fallbackDark) else Color(fallbackLight) }

private fun readScrollbarMetrics(): ScrollbarMetrics =
    if (hostOs.isMacOS) {
        ScrollbarMetrics(
            thumbCornerSize = CornerSize(percent = 100),
            thumbThickness = 8.dp,
            thumbThicknessExpanded = 14.dp,
            minThumbLength = 20.dp,
            trackPadding = PaddingValues(2.dp),
            trackPaddingExpanded = PaddingValues(2.dp),
        )
    } else {
        ScrollbarMetrics(
            thumbCornerSize = CornerSize(0),
            thumbThickness = 8.dp,
            thumbThicknessExpanded = 8.dp,
            minThumbLength = 16.dp,
            trackPadding = PaddingValues(),
            trackPaddingExpanded = PaddingValues(),
        )
    }

public fun ScrollbarVisibility.WhenScrolling.Companion.defaults(
    appearAnimationDuration: Duration = 125.milliseconds,
    disappearAnimationDuration: Duration = 125.milliseconds,
    expandAnimationDuration: Duration = 125.milliseconds,
    lingerDuration: Duration = 700.milliseconds,
): ScrollbarVisibility.WhenScrolling =
    ScrollbarVisibility.WhenScrolling(
        appearAnimationDuration = appearAnimationDuration,
        disappearAnimationDuration = disappearAnimationDuration,
        expandAnimationDuration = expandAnimationDuration,
        lingerDuration = lingerDuration,
    )
