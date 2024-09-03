package org.jetbrains.jewel.bridge.theme

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import org.jetbrains.jewel.bridge.ScrollbarHelper
import org.jetbrains.jewel.bridge.retrieveColor
import org.jetbrains.jewel.ui.component.styling.ScrollbarColors
import org.jetbrains.jewel.ui.component.styling.ScrollbarMetrics
import org.jetbrains.jewel.ui.component.styling.ScrollbarStyle
import org.jetbrains.jewel.ui.component.styling.ScrollbarVisibility
import org.jetbrains.jewel.ui.component.styling.TrackClickBehavior
import org.jetbrains.skiko.hostOs

internal fun readScrollbarStyle(isDark: Boolean): ScrollbarStyle =
    ScrollbarStyle(
        colors = readScrollbarColors(isDark),
        metrics = readScrollbarMetrics(),
        trackClickBehavior = readTrackClickBehavior(),
        scrollbarVisibility = readScrollbarVisibility(),
    )

private fun readScrollbarColors(isDark: Boolean) =
    if (hostOs.isMacOS) {
        readScrollbarMacColors(isDark)
    } else {
        readScrollbarWindowsAndLinuxColors(isDark)
    }

private fun readScrollbarMacColors(isDark: Boolean): ScrollbarColors =
    ScrollbarColors(
        thumbBackground =
            retrieveColor(
                key = "ScrollBar.Mac.Transparent.thumbColor",
                isDark = isDark,
                default = Color(0x00000000),
                defaultDark = Color(0x00808080),
            ),
        thumbBackgroundActive =
            retrieveColor(
                key = "ScrollBar.Mac.Transparent.hoverThumbColor",
                isDark = isDark,
                default = Color(0x80000000),
                defaultDark = Color(0x8C808080),
            ),
        thumbOpaqueBackground =
            retrieveColor(
                key = "ScrollBar.Mac.thumbColor",
                isDark = isDark,
                default = Color(0x33000000),
                defaultDark = Color(0x59808080),
            ),
        thumbOpaqueBackgroundHovered =
            retrieveColor(
                key = "ScrollBar.Mac.hoverThumbColor",
                isDark = isDark,
                default = Color(0x80000000),
                defaultDark = Color(0x8C808080),
            ),
        thumbBorder =
            retrieveColor(
                key = "ScrollBar.Mac.Transparent.thumbBorderColor",
                isDark = isDark,
                default = Color(0x00000000),
                defaultDark = Color(0x00262626),
            ),
        thumbBorderActive =
            retrieveColor(
                key = "ScrollBar.Mac.Transparent.hoverThumbBorderColor",
                isDark = isDark,
                default = Color(0x80000000),
                defaultDark = Color(0x8C262626),
            ),
        thumbOpaqueBorder =
            retrieveColor(
                key = "ScrollBar.Mac.thumbBorderColor",
                isDark = isDark,
                default = Color(0x33000000),
                defaultDark = Color(0x59262626),
            ),
        thumbOpaqueBorderHovered =
            retrieveColor(
                key = "ScrollBar.Mac.hoverThumbBorderColor",
                isDark = isDark,
                default = Color(0x80000000),
                defaultDark = Color(0x8C262626),
            ),
        trackBackground =
            retrieveColor(
                key = "ScrollBar.Mac.Transparent.trackColor",
                isDark = isDark,
                default = Color(0x00808080),
                defaultDark = Color(0x00808080),
            ),
        trackBackgroundExpanded =
            retrieveColor(
                key = "ScrollBar.Mac.Transparent.hoverTrackColor",
                isDark = isDark,
                default = Color(0x1A808080),
                defaultDark = Color(0x1A808080),
            ),
        trackOpaqueBackground =
            retrieveColor(
                key = "ScrollBar.Mac.trackColor",
                isDark = isDark,
                default = Color(0x00808080),
                defaultDark = Color(0x00808080),
            ),
        trackOpaqueBackgroundHovered =
            retrieveColor(
                key = "ScrollBar.Mac.hoverTrackColor",
                isDark = isDark,
                default = Color(0x00808080),
                defaultDark = Color(0x00808080),
            ),
    )

private fun readScrollbarWindowsAndLinuxColors(isDark: Boolean): ScrollbarColors =
    ScrollbarColors(
        thumbBackground =
            retrieveColor(
                key = "ScrollBar.Transparent.thumbColor",
                isDark = isDark,
                default = Color(0x33737373),
                defaultDark = Color(0x47A6A6A6),
            ),
        thumbBackgroundActive =
            retrieveColor(
                key = "ScrollBar.Transparent.hoverThumbColor",
                isDark = isDark,
                default = Color(0x47737373),
                defaultDark = Color(0x59A6A6A6),
            ),
        thumbOpaqueBackground =
            retrieveColor(
                key = "ScrollBar.thumbColor",
                isDark = isDark,
                default = Color(0x33737373),
                defaultDark = Color(0x47A6A6A6),
            ),
        thumbOpaqueBackgroundHovered =
            retrieveColor(
                key = "ScrollBar.hoverThumbColor",
                isDark = isDark,
                default = Color(0x47737373),
                defaultDark = Color(0x59A6A6A6),
            ),
        thumbBorder =
            retrieveColor(
                key = "ScrollBar.Transparent.thumbBorderColor",
                isDark = isDark,
                default = Color(0x33595959),
                defaultDark = Color(0x47383838),
            ),
        thumbBorderActive =
            retrieveColor(
                key = "ScrollBar.Transparent.hoverThumbBorderColor",
                isDark = isDark,
                default = Color(0x47595959),
                defaultDark = Color(0x59383838),
            ),
        thumbOpaqueBorder =
            retrieveColor(
                key = "ScrollBar.thumbBorderColor",
                isDark = isDark,
                default = Color(0x33595959),
                defaultDark = Color(0x47383838),
            ),
        thumbOpaqueBorderHovered =
            retrieveColor(
                key = "ScrollBar.hoverThumbBorderColor",
                isDark = isDark,
                default = Color(0x47595959),
                defaultDark = Color(0x59383838),
            ),
        trackBackground =
            retrieveColor(
                key = "ScrollBar.Transparent.trackColor",
                isDark = isDark,
                default = Color(0x00808080),
                defaultDark = Color(0x00808080),
            ),
        trackBackgroundExpanded =
            retrieveColor(
                key = "ScrollBar.Transparent.hoverTrackColor",
                isDark = isDark,
                default = Color(0x1A808080),
                defaultDark = Color(0x1A808080),
            ),
        trackOpaqueBackground =
            retrieveColor(
                key = "ScrollBar.trackColor",
                isDark = isDark,
                default = Color(0x00808080),
                defaultDark = Color(0x00808080),
            ),
        trackOpaqueBackgroundHovered =
            retrieveColor(
                key = "ScrollBar.hoverTrackColor",
                isDark = isDark,
                default = Color(0x00808080),
                defaultDark = Color(0x00808080),
            ),
    )

private fun readScrollbarMetrics(): ScrollbarMetrics =
    if (hostOs.isMacOS) {
        ScrollbarMetrics(thumbCornerSize = CornerSize(percent = 100), minThumbLength = 24.dp)
    } else {
        ScrollbarMetrics(thumbCornerSize = CornerSize(0), minThumbLength = 24.dp)
    }

private fun readTrackClickBehavior() =
    if (hostOs.isMacOS) {
        ScrollbarHelper.getInstance().trackClickBehaviorFlow.value
    } else {
        TrackClickBehavior.JumpToSpot
    }

private fun readScrollbarVisibility() =
    if (hostOs.isMacOS) {
        ScrollbarHelper.getInstance().scrollbarVisibilityStyleFlow.value
    } else {
        ScrollbarVisibility.AlwaysVisible.windowsAndLinux()
    }

public fun ScrollbarVisibility.WhenScrolling.Companion.default(): ScrollbarVisibility.WhenScrolling =
    if (hostOs.isMacOS) {
        ScrollbarVisibility.WhenScrolling.macOs()
    } else {
        ScrollbarVisibility.WhenScrolling.windowsAndLinux()
    }

public fun ScrollbarVisibility.WhenScrolling.Companion.macOs(
    trackThickness: Dp = 11.dp,
    trackThicknessExpanded: Dp = 14.dp,
    trackPadding: PaddingValues = PaddingValues(2.dp),
    trackPaddingWithBorder: PaddingValues = PaddingValues(1.dp),
    trackColorAnimationDuration: Duration = 125.milliseconds,
    expandAnimationDuration: Duration = trackColorAnimationDuration,
    thumbColorAnimationDuration: Duration = trackColorAnimationDuration,
    lingerDuration: Duration = 700.milliseconds,
): ScrollbarVisibility.WhenScrolling =
    ScrollbarVisibility.WhenScrolling(
        trackThickness = trackThickness,
        trackThicknessExpanded = trackThicknessExpanded,
        trackPadding = trackPadding,
        trackPaddingWithBorder = trackPaddingWithBorder,
        trackColorAnimationDuration = trackColorAnimationDuration,
        expandAnimationDuration = expandAnimationDuration,
        thumbColorAnimationDuration = thumbColorAnimationDuration,
        lingerDuration = lingerDuration,
    )

public fun ScrollbarVisibility.WhenScrolling.Companion.windowsAndLinux(
    trackThickness: Dp = 11.dp,
    trackThicknessExpanded: Dp = 14.dp,
    trackPadding: PaddingValues = PaddingValues(),
    trackPaddingWithBorder: PaddingValues = trackPadding,
    trackColorAnimationDuration: Duration = 125.milliseconds,
    expandAnimationDuration: Duration = trackColorAnimationDuration,
    thumbColorAnimationDuration: Duration = trackColorAnimationDuration,
    lingerDuration: Duration = 700.milliseconds,
): ScrollbarVisibility.WhenScrolling =
    ScrollbarVisibility.WhenScrolling(
        trackThickness = trackThickness,
        trackThicknessExpanded = trackThicknessExpanded,
        trackPadding = trackPadding,
        trackPaddingWithBorder = trackPaddingWithBorder,
        trackColorAnimationDuration = trackColorAnimationDuration,
        expandAnimationDuration = expandAnimationDuration,
        thumbColorAnimationDuration = thumbColorAnimationDuration,
        lingerDuration = lingerDuration,
    )

public fun ScrollbarVisibility.AlwaysVisible.Companion.default(): ScrollbarVisibility.AlwaysVisible =
    if (hostOs.isMacOS) {
        ScrollbarVisibility.AlwaysVisible.macOs()
    } else {
        ScrollbarVisibility.AlwaysVisible.windowsAndLinux()
    }

public fun ScrollbarVisibility.AlwaysVisible.Companion.macOs(
    trackThickness: Dp = 14.dp,
    trackPadding: PaddingValues = PaddingValues(2.dp),
    thumbColorAnimationDuration: Duration = 330.milliseconds,
    scrollbarBackgroundColorLight: Color = retrieveColor("ScrollBar.background", Color(0xFFF5F5F5)),
    scrollbarBackgroundColorDark: Color = retrieveColor("ScrollBar.background", Color(0xFF3F4244)),
): ScrollbarVisibility.AlwaysVisible =
    ScrollbarVisibility.AlwaysVisible(
        trackThickness = trackThickness,
        trackPadding = trackPadding,
        trackPaddingWithBorder = trackPadding,
        thumbColorAnimationDuration = thumbColorAnimationDuration,
        trackColorAnimationDuration = 0.milliseconds,
        scrollbarBackgroundColorLight = scrollbarBackgroundColorLight,
        scrollbarBackgroundColorDark = scrollbarBackgroundColorDark,
    )

public fun ScrollbarVisibility.AlwaysVisible.Companion.windowsAndLinux(
    trackThickness: Dp = 10.dp,
    trackPadding: PaddingValues = PaddingValues(0.5.dp),
    thumbColorAnimationDuration: Duration = 330.milliseconds,
    trackColorAnimationDuration: Duration = thumbColorAnimationDuration,
): ScrollbarVisibility.AlwaysVisible =
    ScrollbarVisibility.AlwaysVisible(
        trackThickness,
        trackPadding,
        trackPadding,
        thumbColorAnimationDuration,
        trackColorAnimationDuration,
        Color.Unspecified,
        Color.Unspecified,
    )
