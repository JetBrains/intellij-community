package org.jetbrains.jewel.intui.core

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
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
    override val contentColor: Color,
) : IntelliJThemeDefinition {

    fun copy(
        isDark: Boolean = this.isDark,
        globalColors: GlobalColors = this.globalColors,
        colorPalette: IntUiThemeColorPalette = this.colorPalette,
        iconData: IntelliJThemeIconData = this.iconData,
        globalMetrics: GlobalMetrics = this.globalMetrics,
        defaultTextStyle: TextStyle = this.defaultTextStyle,
        contentColor: Color = this.contentColor,
    ): IntUiThemeDefinition = IntUiThemeDefinition(
        isDark = isDark,
        globalColors = globalColors,
        colorPalette = colorPalette,
        iconData = iconData,
        globalMetrics = globalMetrics,
        defaultTextStyle = defaultTextStyle,
        contentColor = contentColor,
    )

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
        if (contentColor != other.contentColor) return false

        return true
    }

    override fun hashCode(): Int {
        var result = isDark.hashCode()
        result = 31 * result + globalColors.hashCode()
        result = 31 * result + colorPalette.hashCode()
        result = 31 * result + iconData.hashCode()
        result = 31 * result + globalMetrics.hashCode()
        result = 31 * result + defaultTextStyle.hashCode()
        result = 31 * result + contentColor.hashCode()
        return result
    }
}
