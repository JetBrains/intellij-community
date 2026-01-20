// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jewel.intui.standalone.styling

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import org.jetbrains.jewel.intui.core.theme.IntUiDarkTheme
import org.jetbrains.jewel.intui.core.theme.IntUiLightTheme
import org.jetbrains.jewel.ui.component.styling.SpeedSearchColors
import org.jetbrains.jewel.ui.component.styling.SpeedSearchIcons
import org.jetbrains.jewel.ui.component.styling.SpeedSearchMetrics
import org.jetbrains.jewel.ui.component.styling.SpeedSearchStyle
import org.jetbrains.jewel.ui.icons.AllIconsKeys

/**
 * Creates a [SpeedSearchStyle] with the Int UI light theme defaults.
 *
 * @param colors The color scheme for the speed search input overlay.
 * @param metrics The size and spacing metrics for the speed search input overlay.
 * @param icons The icons used in the speed search input overlay.
 */
public fun SpeedSearchStyle.Companion.light(
    colors: SpeedSearchColors = SpeedSearchColors.light(),
    metrics: SpeedSearchMetrics = SpeedSearchMetrics.defaults(),
    icons: SpeedSearchIcons = SpeedSearchIcons.defaults(),
): SpeedSearchStyle = SpeedSearchStyle(colors, metrics, icons)

/**
 * Creates a [SpeedSearchStyle] with the Int UI dark theme defaults.
 *
 * @param colors The color scheme for the speed search input overlay.
 * @param metrics The size and spacing metrics for the speed search input overlay.
 * @param icons The icons used in the speed search input overlay.
 */
public fun SpeedSearchStyle.Companion.dark(
    colors: SpeedSearchColors = SpeedSearchColors.dark(),
    metrics: SpeedSearchMetrics = SpeedSearchMetrics.defaults(),
    icons: SpeedSearchIcons = SpeedSearchIcons.defaults(),
): SpeedSearchStyle = SpeedSearchStyle(colors, metrics, icons)

/**
 * Creates a [SpeedSearchColors] with the Int UI light theme defaults.
 *
 * @param background The background color of the speed search input overlay.
 * @param border The border color of the speed search input overlay.
 * @param foreground The color of the search text.
 * @param error The color used to indicate an error state (e.g., no results found).
 */
public fun SpeedSearchColors.Companion.light(
    background: Color = IntUiLightTheme.colors.gray(14),
    border: Color = IntUiLightTheme.colors.gray(12),
    foreground: Color = Color.Unspecified,
    error: Color = IntUiLightTheme.colors.red(4),
): SpeedSearchColors = SpeedSearchColors(background, border, foreground, error)

/**
 * Creates a [SpeedSearchColors] with the Int UI dark theme defaults.
 *
 * @param background The background color of the speed search input overlay.
 * @param border The border color of the speed search input overlay.
 * @param foreground The color of the search text.
 * @param error The color used to indicate an error state (e.g., no results found).
 */
public fun SpeedSearchColors.Companion.dark(
    background: Color = IntUiDarkTheme.colors.gray(1),
    border: Color = IntUiDarkTheme.colors.gray(3),
    foreground: Color = IntUiDarkTheme.colors.gray(12),
    error: Color = IntUiDarkTheme.colors.red(7),
): SpeedSearchColors = SpeedSearchColors(background, border, foreground, error)

/** Creates a [SpeedSearchMetrics] with the default values. */
public fun SpeedSearchMetrics.Companion.defaults(): SpeedSearchMetrics = SpeedSearchMetrics(PaddingValues(4.dp))

/** Creates a [SpeedSearchIcons] with the default values. */
public fun SpeedSearchIcons.Companion.defaults(): SpeedSearchIcons = SpeedSearchIcons(AllIconsKeys.Actions.Search)
