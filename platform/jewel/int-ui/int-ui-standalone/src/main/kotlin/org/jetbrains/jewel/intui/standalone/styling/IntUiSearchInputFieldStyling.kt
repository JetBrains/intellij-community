// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jewel.intui.standalone.styling

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.jetbrains.jewel.intui.core.theme.IntUiDarkTheme
import org.jetbrains.jewel.intui.core.theme.IntUiLightTheme
import org.jetbrains.jewel.ui.component.styling.SearchTextFieldColors
import org.jetbrains.jewel.ui.component.styling.SearchTextFieldIcons
import org.jetbrains.jewel.ui.component.styling.SearchTextFieldMetrics
import org.jetbrains.jewel.ui.component.styling.SearchTextFieldStyle
import org.jetbrains.jewel.ui.icon.IconKey
import org.jetbrains.jewel.ui.icons.AllIconsKeys

public fun SearchTextFieldStyle.Companion.light(
    colors: SearchTextFieldColors = SearchTextFieldColors.light(),
    metrics: SearchTextFieldMetrics = SearchTextFieldMetrics.default(),
    icons: SearchTextFieldIcons = SearchTextFieldIcons.default(),
): SearchTextFieldStyle = SearchTextFieldStyle(colors, metrics, icons)

public fun SearchTextFieldColors.Companion.light(
    foreground: Color = Color.Unspecified,
    error: Color = IntUiLightTheme.colors.red(4),
): SearchTextFieldColors = SearchTextFieldColors(foreground, error)

public fun SearchTextFieldStyle.Companion.dark(
    colors: SearchTextFieldColors = SearchTextFieldColors.dark(),
    metrics: SearchTextFieldMetrics = SearchTextFieldMetrics.default(),
    icons: SearchTextFieldIcons = SearchTextFieldIcons.default(),
): SearchTextFieldStyle = SearchTextFieldStyle(colors, metrics, icons)

public fun SearchTextFieldColors.Companion.dark(
    foreground: Color = Color.Unspecified,
    error: Color = IntUiDarkTheme.colors.red(7),
): SearchTextFieldColors = SearchTextFieldColors(foreground, error)

public fun SearchTextFieldMetrics.Companion.default(
    contentPadding: PaddingValues = PaddingValues(horizontal = 2.dp),
    popupContentPadding: PaddingValues = PaddingValues(vertical = 2.dp),
    spaceBetweenIcons: Dp = 6.dp,
): SearchTextFieldMetrics = SearchTextFieldMetrics(contentPadding, popupContentPadding, spaceBetweenIcons)

public fun SearchTextFieldIcons.Companion.default(
    searchIcon: IconKey = AllIconsKeys.Actions.Search,
    searchHistoryIcon: IconKey = AllIconsKeys.Actions.SearchWithHistory,
    clearIcon: IconKey = AllIconsKeys.Actions.Close,
): SearchTextFieldIcons = SearchTextFieldIcons(searchIcon, searchHistoryIcon, clearIcon)
