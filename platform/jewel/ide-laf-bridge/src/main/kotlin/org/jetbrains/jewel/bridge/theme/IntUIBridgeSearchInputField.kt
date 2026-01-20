// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jewel.bridge.theme

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.intellij.ui.JBColor
import org.jetbrains.jewel.bridge.retrieveColor
import org.jetbrains.jewel.bridge.toComposeColor
import org.jetbrains.jewel.ui.component.styling.SearchTextFieldColors
import org.jetbrains.jewel.ui.component.styling.SearchTextFieldIcons
import org.jetbrains.jewel.ui.component.styling.SearchTextFieldMetrics
import org.jetbrains.jewel.ui.component.styling.SearchTextFieldStyle
import org.jetbrains.jewel.ui.icons.AllIconsKeys

internal fun readSearchTextFieldStyle() =
    SearchTextFieldStyle(
        colors =
            SearchTextFieldColors(
                foreground = Color.Unspecified,
                error = retrieveColor("Label.errorForeground", JBColor.RED.toComposeColor()),
            ),
        metrics =
            SearchTextFieldMetrics(
                contentPadding = PaddingValues(horizontal = 2.dp),
                popupContentPadding = PaddingValues(vertical = 2.dp),
                spaceBetweenIcons = 2.dp,
            ),
        icons =
            SearchTextFieldIcons(
                searchIcon = AllIconsKeys.Actions.Search,
                searchHistoryIcon = AllIconsKeys.Actions.SearchWithHistory,
                clearIcon = AllIconsKeys.Actions.Close,
            ),
    )
