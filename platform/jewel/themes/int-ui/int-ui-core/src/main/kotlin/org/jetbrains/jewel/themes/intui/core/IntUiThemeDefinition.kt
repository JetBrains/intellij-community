package org.jetbrains.jewel.themes.intui.core

import androidx.compose.runtime.Immutable
import androidx.compose.ui.text.TextStyle
import org.jetbrains.jewel.GlobalColors
import org.jetbrains.jewel.GlobalMetrics
import org.jetbrains.jewel.IntelliJThemeDefinition
import org.jetbrains.jewel.IntelliJThemeIconData

@Immutable
class IntUiThemeDefinition(
    override val isDark: Boolean,
    override val globalColors: GlobalColors,
    override val colorPalette: IntUiThemeColorPalette,
    override val iconData: IntelliJThemeIconData,
    override val globalMetrics: GlobalMetrics,
    override val defaultTextStyle: TextStyle,
) : IntelliJThemeDefinition {

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as IntUiThemeDefinition

        if (isDark != other.isDark) return false
        if (globalColors != other.globalColors) return false
        if (colorPalette != other.colorPalette) return false
        if (iconData != other.iconData) return false
        if (globalMetrics != other.globalMetrics) return false
        if (defaultTextStyle != other.defaultTextStyle) return false

        return true
    }

    override fun hashCode(): Int {
        var result = isDark.hashCode()
        result = 31 * result + globalColors.hashCode()
        result = 31 * result + colorPalette.hashCode()
        result = 31 * result + iconData.hashCode()
        result = 31 * result + globalMetrics.hashCode()
        result = 31 * result + defaultTextStyle.hashCode()
        return result
    }

    override fun toString(): String =
        "IntUiThemeDefinition(isDark=$isDark, globalColors=$globalColors, colorPalette=$colorPalette, " +
            "iconData=$iconData, metrics=$globalMetrics, defaultTextStyle=$defaultTextStyle)"
}
