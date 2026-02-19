package org.jetbrains.jewel.intui.standalone.theme

import androidx.compose.ui.graphics.Color
import org.jetbrains.jewel.foundation.BorderColors
import org.jetbrains.jewel.foundation.GlobalColors
import org.jetbrains.jewel.foundation.OutlineColors
import org.jetbrains.jewel.foundation.TextColors
import org.jetbrains.jewel.intui.core.theme.IntUiDarkTheme
import org.jetbrains.jewel.intui.core.theme.IntUiLightTheme

/**
 * Provides the default [GlobalColors] for the Jewel Standalone light theme.
 *
 * @param borders The set of border colors to use.
 * @param outlines The set of outline colors to use.
 * @param text The set of text colors to use.
 * @param panelBackground The background color for panels and windows.
 * @param toolwindowBackground The background color for tool windows.
 */
public fun GlobalColors.Companion.light(
    borders: BorderColors = BorderColors.light(),
    outlines: OutlineColors = OutlineColors.light(),
    text: TextColors = TextColors.light(),
    panelBackground: Color = IntUiLightTheme.colors.gray(13),
    toolwindowBackground: Color = Color.Unspecified,
): GlobalColors = GlobalColors(borders, outlines, text, panelBackground, toolwindowBackground)

@Suppress("DEPRECATION")
@Deprecated("Use the variant with toolwindowBackground", level = DeprecationLevel.HIDDEN)
public fun GlobalColors.Companion.light(
    borders: BorderColors = BorderColors.light(),
    outlines: OutlineColors = OutlineColors.light(),
    text: TextColors = TextColors.light(),
    paneBackground: Color = IntUiLightTheme.colors.gray(13),
): GlobalColors = GlobalColors(borders = borders, outlines = outlines, text = text, panelBackground = paneBackground)

/**
 * Provides the default [GlobalColors] for the Jewel Standalone dark theme.
 *
 * @param borders The set of border colors to use.
 * @param outlines The set of outline colors to use.
 * @param text The set of text colors to use.
 * @param panelBackground The background color for panels and windows.
 * @param toolwindowBackground The background color for tool windows.
 */
public fun GlobalColors.Companion.dark(
    borders: BorderColors = BorderColors.dark(),
    outlines: OutlineColors = OutlineColors.dark(),
    text: TextColors = TextColors.dark(),
    panelBackground: Color = IntUiDarkTheme.colors.gray(2),
    toolwindowBackground: Color = Color.Unspecified,
): GlobalColors = GlobalColors(borders, outlines, text, panelBackground, toolwindowBackground)

@Suppress("DEPRECATION")
@Deprecated("Use the variant with toolwindowBackground", level = DeprecationLevel.HIDDEN)
public fun GlobalColors.Companion.dark(
    borders: BorderColors = BorderColors.dark(),
    outlines: OutlineColors = OutlineColors.dark(),
    text: TextColors = TextColors.dark(),
    paneBackground: Color = IntUiDarkTheme.colors.gray(2),
): GlobalColors = GlobalColors(borders = borders, outlines = outlines, text = text, panelBackground = paneBackground)

/**
 * Provides the default [BorderColors] for the Jewel Standalone light theme.
 *
 * @param normal The default border color.
 * @param focused The border color for focused components.
 * @param disabled The border color for disabled components.
 */
public fun BorderColors.Companion.light(
    normal: Color = IntUiLightTheme.colors.gray(12),
    focused: Color = IntUiLightTheme.colors.gray(14),
    disabled: Color = IntUiLightTheme.colors.gray(11),
): BorderColors = BorderColors(normal, focused, disabled)

/**
 * Provides the default [BorderColors] for the Jewel Standalone dark theme.
 *
 * @param normal The default border color.
 * @param focused The border color for focused components.
 * @param disabled The border color for disabled components.
 */
public fun BorderColors.Companion.dark(
    normal: Color = IntUiDarkTheme.colors.gray(1),
    focused: Color = IntUiDarkTheme.colors.gray(2),
    disabled: Color = IntUiDarkTheme.colors.gray(4),
): BorderColors = BorderColors(normal, focused, disabled)

/**
 * Provides the default [TextColors] for the Jewel Standalone light theme.
 *
 * @param normal The default text color.
 * @param selected The text color for selected elements.
 * @param disabled The text color for disabled elements.
 * @param info The text color for informational messages.
 * @param error The text color for error messages.
 */
public fun TextColors.Companion.light(
    normal: Color = IntUiLightTheme.colors.gray(1),
    selected: Color = IntUiLightTheme.colors.gray(1),
    disabled: Color = IntUiLightTheme.colors.gray(8),
    info: Color = IntUiLightTheme.colors.gray(7),
    error: Color = IntUiLightTheme.colors.red(4),
): TextColors = TextColors(normal, selected, disabled, disabledSelected = disabled, info, error, warning = normal)

/**
 * Provides the default [TextColors] for the Jewel Standalone dark theme.
 *
 * @param normal The default text color.
 * @param selected The text color for selected elements.
 * @param disabled The text color for disabled elements.
 * @param info The text color for informational messages.
 * @param error The text color for error messages.
 */
public fun TextColors.Companion.dark(
    normal: Color = IntUiDarkTheme.colors.gray(12),
    selected: Color = IntUiDarkTheme.colors.gray(12),
    disabled: Color = IntUiDarkTheme.colors.gray(6),
    info: Color = IntUiDarkTheme.colors.gray(7),
    error: Color = IntUiDarkTheme.colors.red(7),
): TextColors = TextColors(normal, selected, disabled, disabledSelected = disabled, info, error, warning = normal)

/**
 * Provides the default [OutlineColors] for the Jewel Standalone light theme.
 *
 * @param focused The outline color for focused components.
 * @param focusedWarning The outline color for focused components with a warning.
 * @param focusedError The outline color for focused components with an error.
 * @param warning The outline color for components with a warning.
 * @param error The outline color for components with an error.
 */
public fun OutlineColors.Companion.light(
    focused: Color = IntUiLightTheme.colors.blue(4),
    focusedWarning: Color = IntUiLightTheme.colors.yellow(4),
    focusedError: Color = IntUiLightTheme.colors.red(4),
    warning: Color = IntUiLightTheme.colors.yellow(7),
    error: Color = IntUiLightTheme.colors.red(9),
): OutlineColors = OutlineColors(focused, focusedWarning, focusedError, warning, error)

/**
 * Provides the default [OutlineColors] for the Jewel Standalone dark theme.
 *
 * @param focused The outline color for focused components.
 * @param focusedWarning The outline color for focused components with a warning.
 * @param focusedError The outline color for focused components with an error.
 * @param warning The outline color for components with a warning.
 * @param error The outline color for components with an error.
 */
public fun OutlineColors.Companion.dark(
    focused: Color = IntUiDarkTheme.colors.blue(6),
    focusedWarning: Color = IntUiDarkTheme.colors.yellow(4),
    focusedError: Color = IntUiDarkTheme.colors.red(4),
    warning: Color = IntUiDarkTheme.colors.yellow(2),
    error: Color = IntUiDarkTheme.colors.red(2),
): OutlineColors = OutlineColors(focused, focusedWarning, focusedError, warning, error)
