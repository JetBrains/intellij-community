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

/**
 * Creates a [SearchTextFieldStyle] with the Int UI light theme defaults.
 *
 * @param colors The color scheme for the search text field.
 * @param metrics The size and spacing metrics for the search text field.
 * @param icons The icons used in the search text field.
 */
public fun SearchTextFieldStyle.Companion.light(
    colors: SearchTextFieldColors = SearchTextFieldColors.light(),
    metrics: SearchTextFieldMetrics = SearchTextFieldMetrics.default(),
    icons: SearchTextFieldIcons = SearchTextFieldIcons.default(),
): SearchTextFieldStyle = SearchTextFieldStyle(colors, metrics, icons)

/**
 * Creates a [SearchTextFieldColors] with the Int UI light theme defaults.
 *
 * @param foreground The color of the search text.
 * @param error The color used to indicate an error state (e.g., no results found).
 */
public fun SearchTextFieldColors.Companion.light(
    foreground: Color = Color.Unspecified,
    error: Color = IntUiLightTheme.colors.red(4),
): SearchTextFieldColors = SearchTextFieldColors(foreground, error)

/**
 * Creates a [SearchTextFieldStyle] with the Int UI dark theme defaults.
 *
 * @param colors The color scheme for the search text field.
 * @param metrics The size and spacing metrics for the search text field.
 * @param icons The icons used in the search text field.
 */
public fun SearchTextFieldStyle.Companion.dark(
    colors: SearchTextFieldColors = SearchTextFieldColors.dark(),
    metrics: SearchTextFieldMetrics = SearchTextFieldMetrics.default(),
    icons: SearchTextFieldIcons = SearchTextFieldIcons.default(),
): SearchTextFieldStyle = SearchTextFieldStyle(colors, metrics, icons)

/**
 * Creates a [SearchTextFieldColors] with the Int UI dark theme defaults.
 *
 * @param foreground The color of the search text.
 * @param error The color used to indicate an error state (e.g., no results found).
 */
public fun SearchTextFieldColors.Companion.dark(
    foreground: Color = Color.Unspecified,
    error: Color = IntUiDarkTheme.colors.red(7),
): SearchTextFieldColors = SearchTextFieldColors(foreground, error)

/**
 * Creates a [SearchTextFieldMetrics] with the default values.
 *
 * @param contentPadding The padding applied around the text field content.
 * @param popupContentPadding The padding applied around the content inside the search popup.
 * @param spaceBetweenIcons The spacing between the icons in the search text field.
 */
public fun SearchTextFieldMetrics.Companion.default(
    contentPadding: PaddingValues = PaddingValues(horizontal = 2.dp),
    popupContentPadding: PaddingValues = PaddingValues(vertical = 2.dp),
    spaceBetweenIcons: Dp = 6.dp,
): SearchTextFieldMetrics = SearchTextFieldMetrics(contentPadding, popupContentPadding, spaceBetweenIcons)

/**
 * Creates a [SearchTextFieldIcons] with the default values.
 *
 * @param searchIcon The icon displayed to indicate the search action.
 * @param searchHistoryIcon The icon displayed to indicate access to search history.
 * @param clearIcon The icon displayed to clear the current search text.
 */
public fun SearchTextFieldIcons.Companion.default(
    searchIcon: IconKey = AllIconsKeys.Actions.Search,
    searchHistoryIcon: IconKey = AllIconsKeys.Actions.SearchWithHistory,
    clearIcon: IconKey = AllIconsKeys.Actions.Close,
): SearchTextFieldIcons = SearchTextFieldIcons(searchIcon, searchHistoryIcon, clearIcon)
