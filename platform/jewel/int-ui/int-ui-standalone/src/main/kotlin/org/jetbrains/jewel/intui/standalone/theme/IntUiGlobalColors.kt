package org.jetbrains.jewel.intui.standalone.theme

import androidx.compose.ui.graphics.Color
import org.jetbrains.jewel.foundation.BorderColors
import org.jetbrains.jewel.foundation.GlobalColors
import org.jetbrains.jewel.foundation.OutlineColors
import org.jetbrains.jewel.foundation.TextColors
import org.jetbrains.jewel.intui.core.theme.IntUiDarkTheme
import org.jetbrains.jewel.intui.core.theme.IntUiLightTheme

public fun GlobalColors.Companion.light(
    borders: BorderColors = BorderColors.light(),
    outlines: OutlineColors = OutlineColors.light(),
    text: TextColors = TextColors.light(),
    paneBackground: Color = IntUiLightTheme.colors.gray(13),
): GlobalColors = GlobalColors(borders = borders, outlines = outlines, text = text, panelBackground = paneBackground)

public fun GlobalColors.Companion.dark(
    borders: BorderColors = BorderColors.dark(),
    outlines: OutlineColors = OutlineColors.dark(),
    text: TextColors = TextColors.dark(),
    paneBackground: Color = IntUiDarkTheme.colors.gray(2),
): GlobalColors = GlobalColors(borders = borders, outlines = outlines, text = text, panelBackground = paneBackground)

public fun BorderColors.Companion.light(
    normal: Color = IntUiLightTheme.colors.gray(12),
    focused: Color = IntUiLightTheme.colors.gray(14),
    disabled: Color = IntUiLightTheme.colors.gray(11),
): BorderColors = BorderColors(normal, focused, disabled)

public fun BorderColors.Companion.dark(
    normal: Color = IntUiDarkTheme.colors.gray(1),
    focused: Color = IntUiDarkTheme.colors.gray(2),
    disabled: Color = IntUiDarkTheme.colors.gray(4),
): BorderColors = BorderColors(normal, focused, disabled)

public fun TextColors.Companion.light(
    normal: Color = IntUiLightTheme.colors.gray(1),
    selected: Color = IntUiLightTheme.colors.gray(1),
    disabled: Color = IntUiLightTheme.colors.gray(8),
    info: Color = IntUiLightTheme.colors.gray(7),
    error: Color = IntUiLightTheme.colors.red(4),
): TextColors = TextColors(normal, selected, disabled, disabledSelected = disabled, info, error, warning = normal)

public fun TextColors.Companion.dark(
    normal: Color = IntUiDarkTheme.colors.gray(12),
    selected: Color = IntUiDarkTheme.colors.gray(12),
    disabled: Color = IntUiDarkTheme.colors.gray(6),
    info: Color = IntUiDarkTheme.colors.gray(7),
    error: Color = IntUiDarkTheme.colors.red(7),
): TextColors = TextColors(normal, selected, disabled, disabledSelected = disabled, info, error, warning = normal)

public fun OutlineColors.Companion.light(
    focused: Color = IntUiLightTheme.colors.blue(4),
    focusedWarning: Color = IntUiLightTheme.colors.yellow(4),
    focusedError: Color = IntUiLightTheme.colors.red(4),
    warning: Color = IntUiLightTheme.colors.yellow(7),
    error: Color = IntUiLightTheme.colors.red(9),
): OutlineColors = OutlineColors(focused, focusedWarning, focusedError, warning, error)

public fun OutlineColors.Companion.dark(
    focused: Color = IntUiDarkTheme.colors.blue(6),
    focusedWarning: Color = IntUiDarkTheme.colors.yellow(4),
    focusedError: Color = IntUiDarkTheme.colors.red(4),
    warning: Color = IntUiDarkTheme.colors.yellow(2),
    error: Color = IntUiDarkTheme.colors.red(2),
): OutlineColors = OutlineColors(focused, focusedWarning, focusedError, warning, error)
