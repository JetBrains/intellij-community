package org.jetbrains.jewel.intui.standalone.theme

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import org.jetbrains.jewel.foundation.BorderColors
import org.jetbrains.jewel.foundation.GlobalColors
import org.jetbrains.jewel.foundation.OutlineColors
import org.jetbrains.jewel.intui.core.theme.IntUiDarkTheme
import org.jetbrains.jewel.intui.core.theme.IntUiLightTheme

@Composable
fun GlobalColors.Companion.light(
    borders: BorderColors = BorderColors.light(),
    outlines: OutlineColors = OutlineColors.light(),
    infoContent: Color = IntUiLightTheme.colors.grey(7),
    paneBackground: Color = IntUiLightTheme.colors.grey(13),
) = GlobalColors(
    borders = borders,
    outlines = outlines,
    infoContent = infoContent,
    paneBackground = paneBackground,
)

@Composable
fun GlobalColors.Companion.dark(
    borders: BorderColors = BorderColors.dark(),
    outlines: OutlineColors = OutlineColors.dark(),
    infoContent: Color = IntUiDarkTheme.colors.grey(7),
    paneBackground: Color = IntUiDarkTheme.colors.grey(2),
) = GlobalColors(
    borders = borders,
    outlines = outlines,
    infoContent = infoContent,
    paneBackground = paneBackground,
)

@Composable
fun BorderColors.Companion.light(
    normal: Color = IntUiLightTheme.colors.grey(9),
    focused: Color = IntUiLightTheme.colors.grey(14),
    disabled: Color = IntUiLightTheme.colors.grey(11),
) = BorderColors(normal, focused, disabled)

@Composable
fun BorderColors.Companion.dark(
    normal: Color = IntUiDarkTheme.colors.grey(5),
    focused: Color = IntUiDarkTheme.colors.grey(2),
    disabled: Color = IntUiDarkTheme.colors.grey(4),
) = BorderColors(normal, focused, disabled)

@Composable
fun OutlineColors.Companion.light(
    focused: Color = IntUiLightTheme.colors.blue(4),
    focusedWarning: Color = IntUiLightTheme.colors.yellow(4),
    focusedError: Color = IntUiLightTheme.colors.red(4),
    warning: Color = IntUiLightTheme.colors.yellow(7),
    error: Color = IntUiLightTheme.colors.red(9),
) = OutlineColors(focused, focusedWarning, focusedError, warning, error)

@Composable
fun OutlineColors.Companion.dark(
    focused: Color = IntUiDarkTheme.colors.blue(6),
    focusedWarning: Color = IntUiDarkTheme.colors.yellow(4),
    focusedError: Color = IntUiDarkTheme.colors.red(4),
    warning: Color = IntUiDarkTheme.colors.yellow(2),
    error: Color = IntUiDarkTheme.colors.red(2),
) = OutlineColors(focused, focusedWarning, focusedError, warning, error)
