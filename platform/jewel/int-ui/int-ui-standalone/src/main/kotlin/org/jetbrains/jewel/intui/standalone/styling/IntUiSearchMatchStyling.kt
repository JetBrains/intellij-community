// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jewel.intui.standalone.styling

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import org.jetbrains.jewel.intui.core.theme.IntUiDarkTheme
import org.jetbrains.jewel.intui.core.theme.IntUiLightTheme
import org.jetbrains.jewel.ui.component.styling.SearchMatchColors
import org.jetbrains.jewel.ui.component.styling.SearchMatchMetrics
import org.jetbrains.jewel.ui.component.styling.SearchMatchStyle

/** Creates an Int UI light [SearchMatchStyle] with the provided parameters. */
public fun SearchMatchStyle.Companion.light(
    colors: SearchMatchColors = SearchMatchColors.light(),
    metrics: SearchMatchMetrics = SearchMatchMetrics.default(),
): SearchMatchStyle = SearchMatchStyle(colors, metrics)

/** Creates an Int UI dark [SearchMatchStyle] with the provided parameters. */
public fun SearchMatchStyle.Companion.dark(
    colors: SearchMatchColors = SearchMatchColors.dark(),
    metrics: SearchMatchMetrics = SearchMatchMetrics.default(),
): SearchMatchStyle = SearchMatchStyle(colors, metrics)

/** Creates an Int UI light [SearchMatchColors] with the provided parameters. */
public fun SearchMatchColors.Companion.light(
    startBackground: Color = IntUiLightTheme.colors.yellow(7),
    endBackground: Color = IntUiLightTheme.colors.yellow(7),
    foreground: Color = Color(0xFF323232),
): SearchMatchColors =
    SearchMatchColors(startBackground = startBackground, endBackground = endBackground, foreground = foreground)

/** Creates an Int UI dark [SearchMatchColors] with the provided parameters. */
public fun SearchMatchColors.Companion.dark(
    startBackground: Color = IntUiDarkTheme.colors.yellow(5),
    endBackground: Color = IntUiDarkTheme.colors.yellow(5),
    foreground: Color = Color(0xFF000000),
): SearchMatchColors =
    SearchMatchColors(startBackground = startBackground, endBackground = endBackground, foreground = foreground)

/** Creates an Int UI default [SearchMatchMetrics] with the provided parameters. */
public fun SearchMatchMetrics.Companion.default(
    cornerSize: CornerSize = CornerSize(2.5.dp),
    padding: PaddingValues = PaddingValues(2.dp),
): SearchMatchMetrics = SearchMatchMetrics(cornerSize, padding)
