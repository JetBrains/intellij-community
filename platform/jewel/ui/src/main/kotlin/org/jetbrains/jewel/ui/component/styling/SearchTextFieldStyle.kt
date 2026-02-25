// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jewel.ui.component.styling

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.jetbrains.jewel.foundation.GenerateDataFunctions
import org.jetbrains.jewel.ui.icon.IconKey
import org.jetbrains.jewel.ui.icons.AllIconsKeys

@Immutable
@GenerateDataFunctions
public class SearchTextFieldStyle(
    public val colors: SearchTextFieldColors,
    public val metrics: SearchTextFieldMetrics,
    public val icons: SearchTextFieldIcons,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as SearchTextFieldStyle

        if (colors != other.colors) return false
        if (metrics != other.metrics) return false
        if (icons != other.icons) return false

        return true
    }

    override fun hashCode(): Int {
        var result = colors.hashCode()
        result = 31 * result + metrics.hashCode()
        result = 31 * result + icons.hashCode()
        return result
    }

    override fun toString(): String = "SearchTextFieldStyle(colors=$colors, metrics=$metrics, icons=$icons)"

    public companion object
}

@Immutable
@GenerateDataFunctions
public class SearchTextFieldColors(public val foreground: Color, public val error: Color) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as SearchTextFieldColors

        if (foreground != other.foreground) return false
        if (error != other.error) return false

        return true
    }

    override fun hashCode(): Int {
        var result = foreground.hashCode()
        result = 31 * result + error.hashCode()
        return result
    }

    override fun toString(): String = "SearchInputFieldColors(foreground=$foreground, error=$error)"

    public companion object
}

@Immutable
@GenerateDataFunctions
public class SearchTextFieldMetrics(
    public val contentPadding: PaddingValues,
    public val popupContentPadding: PaddingValues,
    public val spaceBetweenIcons: Dp,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as SearchTextFieldMetrics

        if (contentPadding != other.contentPadding) return false
        if (popupContentPadding != other.popupContentPadding) return false
        if (spaceBetweenIcons != other.spaceBetweenIcons) return false

        return true
    }

    override fun hashCode(): Int {
        var result = contentPadding.hashCode()
        result = 31 * result + popupContentPadding.hashCode()
        result = 31 * result + spaceBetweenIcons.hashCode()
        return result
    }

    override fun toString(): String =
        "SearchInputFieldMetrics(" +
            "contentPadding=$contentPadding, " +
            "popupContentPadding=$popupContentPadding, " +
            "spaceBetweenIcons=$spaceBetweenIcons" +
            ")"

    public companion object
}

@Immutable
@GenerateDataFunctions
public class SearchTextFieldIcons(
    public val searchIcon: IconKey,
    public val searchHistoryIcon: IconKey,
    public val clearIcon: IconKey,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as SearchTextFieldIcons

        if (searchIcon != other.searchIcon) return false
        if (searchHistoryIcon != other.searchHistoryIcon) return false
        if (clearIcon != other.clearIcon) return false

        return true
    }

    override fun hashCode(): Int {
        var result = searchIcon.hashCode()
        result = 31 * result + searchHistoryIcon.hashCode()
        result = 31 * result + clearIcon.hashCode()
        return result
    }

    override fun toString(): String =
        "SearchInputFieldIcons(searchIcon=$searchIcon, searchHistoryIcon=$searchHistoryIcon, clearIcon=$clearIcon)"

    public companion object
}

internal fun fallbackSearchTextField() =
    SearchTextFieldStyle(
        colors = SearchTextFieldColors(foreground = Color.Unspecified, error = Color(0xFFDB3B4B)),
        metrics =
            SearchTextFieldMetrics(
                contentPadding = PaddingValues(horizontal = 2.dp),
                popupContentPadding = PaddingValues(vertical = 2.dp),
                spaceBetweenIcons = 6.dp,
            ),
        icons =
            SearchTextFieldIcons(
                searchIcon = AllIconsKeys.Actions.Search,
                searchHistoryIcon = AllIconsKeys.Actions.SearchWithHistory,
                clearIcon = AllIconsKeys.Actions.Close,
            ),
    )

public val LocalSearchTextFieldStyle: ProvidableCompositionLocal<SearchTextFieldStyle> = staticCompositionLocalOf {
    error("No SearchTextFieldStyle provided. Have you forgotten the theme?")
}
