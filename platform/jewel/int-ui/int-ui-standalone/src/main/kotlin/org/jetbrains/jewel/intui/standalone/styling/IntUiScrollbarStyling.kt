package org.jetbrains.jewel.intui.standalone.styling

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import org.jetbrains.jewel.ui.component.styling.ScrollbarColors
import org.jetbrains.jewel.ui.component.styling.ScrollbarMetrics
import org.jetbrains.jewel.ui.component.styling.ScrollbarStyle
import org.jetbrains.jewel.ui.component.styling.ScrollbarVisibility
import org.jetbrains.jewel.ui.component.styling.ScrollbarVisibility.AlwaysVisible
import org.jetbrains.jewel.ui.component.styling.ScrollbarVisibility.WhenScrolling
import org.jetbrains.jewel.ui.component.styling.TrackClickBehavior
import org.jetbrains.skiko.hostOs

/**
 * Creates a light-themed [ScrollbarStyle] appropriate for the current operating system.
 *
 * On macOS, this returns [macOsLight]; on Windows and Linux, this returns [windowsAndLinuxLight].
 *
 * @return A [ScrollbarStyle] configured for light theme on the current OS.
 */
public fun ScrollbarStyle.Companion.light(): ScrollbarStyle =
    if (hostOs.isMacOS) {
        ScrollbarStyle.macOsLight()
    } else {
        ScrollbarStyle.windowsAndLinuxLight()
    }

/**
 * Creates a dark-themed [ScrollbarStyle] appropriate for the current operating system.
 *
 * On macOS, this returns [macOsDark]; on Windows and Linux, this returns [windowsAndLinuxDark].
 *
 * @return A [ScrollbarStyle] configured for dark theme on the current OS.
 */
public fun ScrollbarStyle.Companion.dark(): ScrollbarStyle =
    if (hostOs.isMacOS) {
        ScrollbarStyle.macOsDark()
    } else {
        ScrollbarStyle.windowsAndLinuxDark()
    }

/**
 * Creates a light-themed [ScrollbarStyle] specifically designed for macOS.
 *
 * @param colors The color scheme for the scrollbar.
 * @param metrics The sizing and shape properties.
 * @param trackClickBehavior The behavior when clicking on the track.
 * @param scrollbarVisibility The visibility behavior of the scrollbar.
 * @return A [ScrollbarStyle] configured for light theme on macOS.
 */
public fun ScrollbarStyle.Companion.macOsLight(
    colors: ScrollbarColors = ScrollbarColors.macOsLight(),
    metrics: ScrollbarMetrics = ScrollbarMetrics.macOs(),
    trackClickBehavior: TrackClickBehavior = TrackClickBehavior.NextPage,
    scrollbarVisibility: ScrollbarVisibility = WhenScrolling.default(),
): ScrollbarStyle =
    ScrollbarStyle(
        colors = colors,
        metrics = metrics,
        trackClickBehavior = trackClickBehavior,
        scrollbarVisibility = scrollbarVisibility,
    )

/**
 * Creates a dark-themed [ScrollbarStyle] specifically designed for macOS.
 *
 * @param colors The color scheme for the scrollbar.
 * @param metrics The sizing and shape properties.
 * @param trackClickBehavior The behavior when clicking on the track.
 * @param scrollbarVisibility The visibility behavior of the scrollbar.
 * @return A [ScrollbarStyle] configured for dark theme on macOS.
 */
public fun ScrollbarStyle.Companion.macOsDark(
    colors: ScrollbarColors = ScrollbarColors.macOsDark(),
    metrics: ScrollbarMetrics = ScrollbarMetrics.macOs(),
    trackClickBehavior: TrackClickBehavior = TrackClickBehavior.NextPage,
    scrollbarVisibility: ScrollbarVisibility = WhenScrolling.default(),
): ScrollbarStyle =
    ScrollbarStyle(
        colors = colors,
        metrics = metrics,
        trackClickBehavior = trackClickBehavior,
        scrollbarVisibility = scrollbarVisibility,
    )

/**
 * Creates a light-themed [ScrollbarStyle] specifically designed for Windows and Linux.
 *
 * @param colors The color scheme for the scrollbar.
 * @param metrics The sizing and shape properties.
 * @param trackClickBehavior The behavior when clicking on the track.
 * @param scrollbarVisibility The visibility behavior of the scrollbar.
 * @return A [ScrollbarStyle] configured for light theme on Windows and Linux.
 */
public fun ScrollbarStyle.Companion.windowsAndLinuxLight(
    colors: ScrollbarColors = ScrollbarColors.windowsAndLinuxLight(),
    metrics: ScrollbarMetrics = ScrollbarMetrics.windowsAndLinux(),
    trackClickBehavior: TrackClickBehavior = TrackClickBehavior.JumpToSpot,
    scrollbarVisibility: AlwaysVisible = AlwaysVisible.windowsAndLinux(),
): ScrollbarStyle =
    ScrollbarStyle(
        colors = colors,
        metrics = metrics,
        trackClickBehavior = trackClickBehavior,
        scrollbarVisibility = scrollbarVisibility,
    )

/**
 * Creates a dark-themed [ScrollbarStyle] specifically designed for Windows and Linux.
 *
 * @param colors The color scheme for the scrollbar.
 * @param metrics The sizing and shape properties.
 * @param trackClickBehavior The behavior when clicking on the track.
 * @param scrollbarVisibility The visibility behavior of the scrollbar.
 * @return A [ScrollbarStyle] configured for dark theme on Windows and Linux.
 */
public fun ScrollbarStyle.Companion.windowsAndLinuxDark(
    colors: ScrollbarColors = ScrollbarColors.windowsAndLinuxDark(),
    metrics: ScrollbarMetrics = ScrollbarMetrics.windowsAndLinux(),
    trackClickBehavior: TrackClickBehavior = TrackClickBehavior.JumpToSpot,
    scrollbarVisibility: AlwaysVisible = AlwaysVisible.windowsAndLinux(),
): ScrollbarStyle =
    ScrollbarStyle(
        colors = colors,
        metrics = metrics,
        trackClickBehavior = trackClickBehavior,
        scrollbarVisibility = scrollbarVisibility,
    )

/**
 * Creates a light-themed [ScrollbarColors] specifically designed for macOS.
 *
 * @param thumbBackground The background color of the scrollbar thumb.
 * @param thumbBackgroundActive The background color of the scrollbar thumb when active.
 * @param thumbOpaqueBackground The background color of the scrollbar thumb when in opaque mode.
 * @param thumbOpaqueBackgroundHovered The background color of the scrollbar thumb when hovered and in opaque mode.
 * @param thumbBorder The border color of the scrollbar thumb.
 * @param thumbBorderActive The border color of the scrollbar thumb when active.
 * @param thumbOpaqueBorder The border color of the scrollbar thumb when in opaque mode.
 * @param thumbOpaqueBorderHovered The border color of the scrollbar thumb when hovered and in opaque mode.
 * @param trackBackground The background color of the scrollbar track.
 * @param trackBackgroundHovered The background color of the scrollbar track when hovered.
 * @param trackOpaqueBackground The background color of the scrollbar track when in opaque mode.
 * @param trackOpaqueBackgroundHovered The background color of the scrollbar track when hovered and in opaque mode.
 * @return A [ScrollbarColors] configured for light theme on macOS.
 */
public fun ScrollbarColors.Companion.macOsLight(
    thumbBackground: Color = Color(0x00000000),
    thumbBackgroundActive: Color = Color(0x80000000),
    thumbOpaqueBackground: Color = Color(0x33000000),
    thumbOpaqueBackgroundHovered: Color = thumbBackgroundActive,
    thumbBorder: Color = thumbBackground,
    thumbBorderActive: Color = thumbBackgroundActive,
    thumbOpaqueBorder: Color = thumbOpaqueBackground,
    thumbOpaqueBorderHovered: Color = thumbBackgroundActive,
    trackBackground: Color = Color(0x00808080),
    trackBackgroundHovered: Color = Color(0x1A808080),
    trackOpaqueBackground: Color = trackBackground,
    trackOpaqueBackgroundHovered: Color = trackBackground,
): ScrollbarColors =
    ScrollbarColors(
        thumbBackground = thumbBackground,
        thumbBackgroundActive = thumbBackgroundActive,
        thumbOpaqueBackground = thumbOpaqueBackground,
        thumbOpaqueBackgroundHovered = thumbOpaqueBackgroundHovered,
        thumbBorder = thumbBorder,
        thumbBorderActive = thumbBorderActive,
        thumbOpaqueBorder = thumbOpaqueBorder,
        thumbOpaqueBorderHovered = thumbOpaqueBorderHovered,
        trackBackground = trackBackground,
        trackBackgroundExpanded = trackBackgroundHovered,
        trackOpaqueBackground = trackOpaqueBackground,
        trackOpaqueBackgroundHovered = trackOpaqueBackgroundHovered,
    )

/**
 * Creates a dark-themed [ScrollbarColors] specifically designed for macOS.
 *
 * @param thumbBackground The background color of the scrollbar thumb.
 * @param thumbBackgroundActive The background color of the scrollbar thumb when active.
 * @param thumbOpaqueBackground The background color of the scrollbar thumb when in opaque mode.
 * @param thumbOpaqueBackgroundHovered The background color of the scrollbar thumb when hovered and in opaque mode.
 * @param thumbBorder The border color of the scrollbar thumb.
 * @param thumbBorderActive The border color of the scrollbar thumb when active.
 * @param thumbOpaqueBorder The border color of the scrollbar thumb when in opaque mode.
 * @param thumbOpaqueBorderHovered The border color of the scrollbar thumb when hovered and in opaque mode.
 * @param trackBackground The background color of the scrollbar track.
 * @param trackBackgroundHovered The background color of the scrollbar track when hovered.
 * @param trackOpaqueBackground The background color of the scrollbar track when in opaque mode.
 * @param trackOpaqueBackgroundHovered The background color of the scrollbar track when hovered and in opaque mode.
 * @return A [ScrollbarColors] configured for dark theme on macOS.
 */
public fun ScrollbarColors.Companion.macOsDark(
    thumbBackground: Color = Color(0x00808080),
    thumbBackgroundActive: Color = Color(0x8C808080),
    thumbOpaqueBackground: Color = Color(0x59808080),
    thumbOpaqueBackgroundHovered: Color = thumbBackgroundActive,
    thumbBorder: Color = Color(0x00262626),
    thumbBorderActive: Color = Color(0x8C262626),
    thumbOpaqueBorder: Color = Color(0x59262626),
    thumbOpaqueBorderHovered: Color = thumbBorderActive,
    trackBackground: Color = Color(0x00808080),
    trackBackgroundHovered: Color = Color(0x1A808080),
    trackOpaqueBackground: Color = trackBackground,
    trackOpaqueBackgroundHovered: Color = trackBackground,
): ScrollbarColors =
    ScrollbarColors(
        thumbBackground = thumbBackground,
        thumbBackgroundActive = thumbBackgroundActive,
        thumbOpaqueBackground = thumbOpaqueBackground,
        thumbOpaqueBackgroundHovered = thumbOpaqueBackgroundHovered,
        thumbBorder = thumbBorder,
        thumbBorderActive = thumbBorderActive,
        thumbOpaqueBorder = thumbOpaqueBorder,
        thumbOpaqueBorderHovered = thumbOpaqueBorderHovered,
        trackBackground = trackBackground,
        trackBackgroundExpanded = trackBackgroundHovered,
        trackOpaqueBackground = trackOpaqueBackground,
        trackOpaqueBackgroundHovered = trackOpaqueBackgroundHovered,
    )

/**
 * Creates a light-themed [ScrollbarColors] specifically designed for Windows and Linux.
 *
 * @param thumbBackground The background color of the scrollbar thumb.
 * @param thumbBackgroundActive The background color of the scrollbar thumb when active.
 * @param thumbOpaqueBackground The background color of the scrollbar thumb when in opaque mode.
 * @param thumbOpaqueBackgroundHovered The background color of the scrollbar thumb when hovered and in opaque mode.
 * @param thumbBorder The border color of the scrollbar thumb.
 * @param thumbBorderActive The border color of the scrollbar thumb when active.
 * @param thumbOpaqueBorder The border color of the scrollbar thumb when in opaque mode.
 * @param thumbOpaqueBorderHovered The border color of the scrollbar thumb when hovered and in opaque mode.
 * @param trackBackground The background color of the scrollbar track.
 * @param trackBackgroundHovered The background color of the scrollbar track when hovered.
 * @param trackOpaqueBackground The background color of the scrollbar track when in opaque mode.
 * @param trackOpaqueBackgroundHovered The background color of the scrollbar track when hovered and in opaque mode.
 * @return A [ScrollbarColors] configured for light theme on Windows and Linux.
 */
public fun ScrollbarColors.Companion.windowsAndLinuxLight(
    thumbBackground: Color = Color(0x33737373),
    thumbBackgroundActive: Color = Color(0x47737373),
    thumbOpaqueBackground: Color = thumbBackground,
    thumbOpaqueBackgroundHovered: Color = thumbBackgroundActive,
    thumbBorder: Color = Color(0x33595959),
    thumbBorderActive: Color = Color(0x47595959),
    thumbOpaqueBorder: Color = thumbBorder,
    thumbOpaqueBorderHovered: Color = thumbBorderActive,
    trackBackground: Color = Color(0x00808080),
    trackBackgroundHovered: Color = Color(0x1A808080),
    trackOpaqueBackground: Color = trackBackground,
    trackOpaqueBackgroundHovered: Color = trackBackgroundHovered,
): ScrollbarColors =
    ScrollbarColors(
        thumbBackground = thumbBackground,
        thumbBackgroundActive = thumbBackgroundActive,
        thumbOpaqueBackground = thumbOpaqueBackground,
        thumbOpaqueBackgroundHovered = thumbOpaqueBackgroundHovered,
        thumbBorder = thumbBorder,
        thumbBorderActive = thumbBorderActive,
        thumbOpaqueBorder = thumbOpaqueBorder,
        thumbOpaqueBorderHovered = thumbOpaqueBorderHovered,
        trackBackground = trackBackground,
        trackBackgroundExpanded = trackBackgroundHovered,
        trackOpaqueBackground = trackOpaqueBackground,
        trackOpaqueBackgroundHovered = trackOpaqueBackgroundHovered,
    )

/**
 * Creates a dark-themed [ScrollbarColors] specifically designed for Windows and Linux.
 *
 * @param thumbBackground The background color of the scrollbar thumb.
 * @param thumbBackgroundActive The background color of the scrollbar thumb when active.
 * @param thumbOpaqueBackground The background color of the scrollbar thumb when in opaque mode.
 * @param thumbOpaqueBackgroundHovered The background color of the scrollbar thumb when hovered and in opaque mode.
 * @param thumbBorder The border color of the scrollbar thumb.
 * @param thumbBorderActive The border color of the scrollbar thumb when active.
 * @param thumbOpaqueBorder The border color of the scrollbar thumb when in opaque mode.
 * @param thumbOpaqueBorderHovered The border color of the scrollbar thumb when hovered and in opaque mode.
 * @param trackBackground The background color of the scrollbar track.
 * @param trackBackgroundHovered The background color of the scrollbar track when hovered.
 * @param trackOpaqueBackground The background color of the scrollbar track when in opaque mode.
 * @param trackOpaqueBackgroundHovered The background color of the scrollbar track when hovered and in opaque mode.
 * @return A [ScrollbarColors] configured for dark theme on Windows and Linux.
 */
public fun ScrollbarColors.Companion.windowsAndLinuxDark(
    thumbBackground: Color = Color(0x47A6A6A6),
    thumbBackgroundActive: Color = Color(0x59A6A6A6),
    thumbOpaqueBackground: Color = thumbBackground,
    thumbOpaqueBackgroundHovered: Color = thumbBackgroundActive,
    thumbBorder: Color = Color(0x47383838),
    thumbBorderActive: Color = Color(0x59383838),
    thumbOpaqueBorder: Color = thumbBorder,
    thumbOpaqueBorderHovered: Color = thumbBorderActive,
    trackBackground: Color = Color(0x00808080),
    trackBackgroundHovered: Color = Color(0x1A808080),
    trackOpaqueBackground: Color = trackBackground,
    trackOpaqueBackgroundHovered: Color = trackBackgroundHovered,
): ScrollbarColors =
    ScrollbarColors(
        thumbBackground = thumbBackground,
        thumbBackgroundActive = thumbBackgroundActive,
        thumbOpaqueBackground = thumbOpaqueBackground,
        thumbOpaqueBackgroundHovered = thumbOpaqueBackgroundHovered,
        thumbBorder = thumbBorder,
        thumbBorderActive = thumbBorderActive,
        thumbOpaqueBorder = thumbOpaqueBorder,
        thumbOpaqueBorderHovered = thumbOpaqueBorderHovered,
        trackBackground = trackBackground,
        trackBackgroundExpanded = trackBackgroundHovered,
        trackOpaqueBackground = trackOpaqueBackground,
        trackOpaqueBackgroundHovered = trackOpaqueBackgroundHovered,
    )

/**
 * Creates [ScrollbarMetrics] specifically designed for macOS.
 *
 * @param thumbCornerSize The corner size of the scrollbar thumb.
 * @param minThumbLength The minimum length of the scrollbar thumb.
 * @return A [ScrollbarMetrics] configured for macOS.
 */
public fun ScrollbarMetrics.Companion.macOs(
    thumbCornerSize: CornerSize = CornerSize(100),
    minThumbLength: Dp = 24.dp,
): ScrollbarMetrics = ScrollbarMetrics(thumbCornerSize, minThumbLength)

/**
 * Creates [ScrollbarMetrics] specifically designed for Windows and Linux.
 *
 * @param thumbCornerSize The corner size of the scrollbar thumb.
 * @param minThumbLength The minimum length of the scrollbar thumb.
 * @return A [ScrollbarMetrics] configured for Windows and Linux.
 */
public fun ScrollbarMetrics.Companion.windowsAndLinux(
    thumbCornerSize: CornerSize = CornerSize(0),
    minThumbLength: Dp = 24.dp,
): ScrollbarMetrics = ScrollbarMetrics(thumbCornerSize, minThumbLength)

/**
 * Creates an [AlwaysVisible] scrollbar visibility configuration appropriate for the current operating system.
 *
 * On macOS, this returns [macOs]. On Windows and Linux, this returns [windowsAndLinux].
 *
 * @return An [AlwaysVisible] configuration for the current OS.
 */
public fun AlwaysVisible.Companion.default(): AlwaysVisible =
    if (hostOs.isMacOS) {
        AlwaysVisible.macOs()
    } else {
        AlwaysVisible.windowsAndLinux()
    }

/**
 * Creates an [AlwaysVisible] scrollbar visibility configuration specifically designed for macOS.
 *
 * @param trackThickness The thickness of the scrollbar track.
 * @param trackPadding The padding around the scrollbar track.
 * @param thumbColorAnimationDuration The duration of thumb color animations.
 * @param scrollbarBackgroundColorLight The background color of the scrollbar in light theme.
 * @param scrollbarBackgroundColorDark The background color of the scrollbar in dark theme.
 * @return An [AlwaysVisible] configuration for macOS.
 */
public fun AlwaysVisible.Companion.macOs(
    trackThickness: Dp = 14.dp,
    trackPadding: PaddingValues = PaddingValues(2.dp),
    thumbColorAnimationDuration: Duration = 330.milliseconds,
    scrollbarBackgroundColorLight: Color = Color(0xFFF5F5F5),
    scrollbarBackgroundColorDark: Color = Color(0xFF3F4244),
): AlwaysVisible =
    AlwaysVisible(
        trackThickness = trackThickness,
        trackPadding = trackPadding,
        trackPaddingWithBorder = trackPadding,
        thumbColorAnimationDuration = thumbColorAnimationDuration,
        trackColorAnimationDuration = 0.milliseconds,
        scrollbarBackgroundColorLight = scrollbarBackgroundColorLight,
        scrollbarBackgroundColorDark = scrollbarBackgroundColorDark,
    )

/**
 * Creates an [AlwaysVisible] scrollbar visibility configuration specifically designed for Windows and Linux.
 *
 * On Windows and Linux, the `AlwaysVisible` behavior matches the `WhenScrolling` behavior, as there is no equivalent to
 * the macOS behavior.
 *
 * @param trackThickness The thickness of the scrollbar track.
 * @param trackPadding The padding around the scrollbar track.
 * @param thumbColorAnimationDuration The duration of thumb color animations.
 * @param trackColorAnimationDuration The duration of track color animations.
 * @return An [AlwaysVisible] configuration for Windows and Linux.
 */
public fun AlwaysVisible.Companion.windowsAndLinux(
    trackThickness: Dp = 10.dp,
    trackPadding: PaddingValues = PaddingValues(0.dp),
    thumbColorAnimationDuration: Duration = 330.milliseconds,
    trackColorAnimationDuration: Duration = thumbColorAnimationDuration,
): AlwaysVisible =
    AlwaysVisible(
        trackThickness = trackThickness,
        trackPadding = trackPadding,
        trackPaddingWithBorder = trackPadding,
        thumbColorAnimationDuration = thumbColorAnimationDuration,
        trackColorAnimationDuration = trackColorAnimationDuration,
        scrollbarBackgroundColorLight = Color.Unspecified,
        scrollbarBackgroundColorDark = Color.Unspecified,
    )

/**
 * Creates a [WhenScrolling] scrollbar visibility configuration with custom parameters.
 *
 * @param trackThickness The thickness of the scrollbar track when not expanded.
 * @param trackThicknessExpanded The thickness of the scrollbar track when expanded.
 * @param trackPadding The padding around the scrollbar track.
 * @param trackPaddingWithBorder The padding around the scrollbar track when it has a border.
 * @param trackColorAnimationDuration The duration of track color animations.
 * @param expandAnimationDuration The duration of track expansion animations.
 * @param thumbColorAnimationDuration The duration of thumb color animations.
 * @param lingerDuration The duration the scrollbar remains visible after scrolling stops.
 * @return A [WhenScrolling] configuration with the specified parameters.
 */
@Deprecated("Use the variant without parameters to get the defaults", ReplaceWith("WhenScrolling.Companion.default()"))
public fun WhenScrolling.Companion.default(
    trackThickness: Dp = 11.dp,
    trackThicknessExpanded: Dp = 14.dp,
    trackPadding: PaddingValues = PaddingValues(2.dp),
    trackPaddingWithBorder: PaddingValues = PaddingValues(1.dp),
    trackColorAnimationDuration: Duration = 125.milliseconds,
    expandAnimationDuration: Duration = trackColorAnimationDuration,
    thumbColorAnimationDuration: Duration = trackColorAnimationDuration,
    lingerDuration: Duration = 700.milliseconds,
): WhenScrolling =
    WhenScrolling(
        trackThickness = trackThickness,
        trackThicknessExpanded = trackThicknessExpanded,
        trackPadding = trackPadding,
        trackPaddingWithBorder = trackPaddingWithBorder,
        trackColorAnimationDuration = trackColorAnimationDuration,
        expandAnimationDuration = expandAnimationDuration,
        thumbColorAnimationDuration = thumbColorAnimationDuration,
        lingerDuration = lingerDuration,
    )

/**
 * Creates a [WhenScrolling] scrollbar visibility configuration appropriate for the current operating system.
 *
 * On macOS, this returns [macOs]. On Windows and Linux, this returns [windowsAndLinux].
 *
 * @return A [WhenScrolling] configuration for the current OS.
 */
public fun WhenScrolling.Companion.default(): WhenScrolling =
    if (hostOs.isMacOS) {
        WhenScrolling.macOs()
    } else {
        WhenScrolling.windowsAndLinux()
    }

/**
 * Creates a [WhenScrolling] scrollbar visibility configuration specifically designed for macOS.
 *
 * @param trackThickness The thickness of the scrollbar track when not expanded.
 * @param trackThicknessExpanded The thickness of the scrollbar track when expanded.
 * @param trackPadding The padding around the scrollbar track.
 * @param trackPaddingWithBorder The padding around the scrollbar track when it has a border.
 * @param trackColorAnimationDuration The duration of track color animations.
 * @param expandAnimationDuration The duration of track expansion animations.
 * @param thumbColorAnimationDuration The duration of thumb color animations.
 * @param lingerDuration The duration the scrollbar remains visible after scrolling stops.
 * @return A [WhenScrolling] configuration for macOS.
 */
public fun WhenScrolling.Companion.macOs(
    trackThickness: Dp = 11.dp,
    trackThicknessExpanded: Dp = 14.dp,
    trackPadding: PaddingValues = PaddingValues(2.dp),
    trackPaddingWithBorder: PaddingValues = PaddingValues(1.dp),
    trackColorAnimationDuration: Duration = 125.milliseconds,
    expandAnimationDuration: Duration = trackColorAnimationDuration,
    thumbColorAnimationDuration: Duration = trackColorAnimationDuration,
    lingerDuration: Duration = 700.milliseconds,
): WhenScrolling =
    WhenScrolling(
        trackThickness = trackThickness,
        trackThicknessExpanded = trackThicknessExpanded,
        trackPadding = trackPadding,
        trackPaddingWithBorder = trackPaddingWithBorder,
        trackColorAnimationDuration = trackColorAnimationDuration,
        expandAnimationDuration = expandAnimationDuration,
        thumbColorAnimationDuration = thumbColorAnimationDuration,
        lingerDuration = lingerDuration,
    )

/**
 * Creates a [WhenScrolling] scrollbar visibility configuration specifically designed for Windows and Linux.
 *
 * @param trackThickness The thickness of the scrollbar track when not expanded.
 * @param trackThicknessExpanded The thickness of the scrollbar track when expanded.
 * @param trackPadding The padding around the scrollbar track.
 * @param trackPaddingWithBorder The padding around the scrollbar track when it has a border.
 * @param trackColorAnimationDuration The duration of track color animations.
 * @param expandAnimationDuration The duration of track expansion animations.
 * @param thumbColorAnimationDuration The duration of thumb color animations.
 * @param lingerDuration The duration the scrollbar remains visible after scrolling stops.
 * @return A [WhenScrolling] configuration for Windows and Linux.
 */
public fun WhenScrolling.Companion.windowsAndLinux(
    trackThickness: Dp = 10.dp,
    trackThicknessExpanded: Dp = 10.dp,
    trackPadding: PaddingValues = PaddingValues(0.dp),
    trackPaddingWithBorder: PaddingValues = PaddingValues(0.dp),
    trackColorAnimationDuration: Duration = 330.milliseconds,
    expandAnimationDuration: Duration = 0.milliseconds,
    thumbColorAnimationDuration: Duration = trackColorAnimationDuration,
    lingerDuration: Duration = 200.milliseconds,
): WhenScrolling =
    WhenScrolling(
        trackThickness = trackThickness,
        trackThicknessExpanded = trackThicknessExpanded,
        trackPadding = trackPadding,
        trackPaddingWithBorder = trackPaddingWithBorder,
        trackColorAnimationDuration = trackColorAnimationDuration,
        expandAnimationDuration = expandAnimationDuration,
        thumbColorAnimationDuration = thumbColorAnimationDuration,
        lingerDuration = lingerDuration,
    )
