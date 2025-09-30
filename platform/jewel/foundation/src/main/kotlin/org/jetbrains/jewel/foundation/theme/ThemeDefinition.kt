package org.jetbrains.jewel.foundation.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import org.jetbrains.jewel.foundation.DisabledAppearanceValues
import org.jetbrains.jewel.foundation.GenerateDataFunctions
import org.jetbrains.jewel.foundation.GlobalColors
import org.jetbrains.jewel.foundation.GlobalMetrics

@Immutable
@GenerateDataFunctions
public class ThemeDefinition(
    public val name: String,
    public val isDark: Boolean,
    public val globalColors: GlobalColors,
    public val globalMetrics: GlobalMetrics,
    public val defaultTextStyle: TextStyle,
    public val editorTextStyle: TextStyle,
    public val consoleTextStyle: TextStyle,
    public val contentColor: Color,
    public val colorPalette: ThemeColorPalette,
    public val iconData: ThemeIconData,
    public val disabledAppearanceValues: DisabledAppearanceValues,
) {
    @Deprecated("Use the primary constructor and provide DisabledAppearanceValues.")
    public constructor(
        name: String,
        isDark: Boolean,
        globalColors: GlobalColors,
        globalMetrics: GlobalMetrics,
        defaultTextStyle: TextStyle,
        editorTextStyle: TextStyle,
        consoleTextStyle: TextStyle,
        contentColor: Color,
        colorPalette: ThemeColorPalette,
        iconData: ThemeIconData,
    ) : this(
        name,
        isDark,
        globalColors,
        globalMetrics,
        defaultTextStyle,
        editorTextStyle,
        consoleTextStyle,
        contentColor,
        colorPalette,
        iconData,
        DisabledAppearanceValues(0, 0, 0),
    )

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as ThemeDefinition

        if (isDark != other.isDark) return false
        if (name != other.name) return false
        if (globalColors != other.globalColors) return false
        if (globalMetrics != other.globalMetrics) return false
        if (defaultTextStyle != other.defaultTextStyle) return false
        if (editorTextStyle != other.editorTextStyle) return false
        if (consoleTextStyle != other.consoleTextStyle) return false
        if (contentColor != other.contentColor) return false
        if (colorPalette != other.colorPalette) return false
        if (iconData != other.iconData) return false
        if (disabledAppearanceValues != other.disabledAppearanceValues) return false

        return true
    }

    override fun hashCode(): Int {
        var result = isDark.hashCode()
        result = 31 * result + name.hashCode()
        result = 31 * result + globalColors.hashCode()
        result = 31 * result + globalMetrics.hashCode()
        result = 31 * result + defaultTextStyle.hashCode()
        result = 31 * result + editorTextStyle.hashCode()
        result = 31 * result + consoleTextStyle.hashCode()
        result = 31 * result + contentColor.hashCode()
        result = 31 * result + colorPalette.hashCode()
        result = 31 * result + iconData.hashCode()
        result = 31 * result + disabledAppearanceValues.hashCode()
        return result
    }

    override fun toString(): String {
        return "ThemeDefinition(" +
            "name='$name', " +
            "isDark=$isDark, " +
            "globalColors=$globalColors, " +
            "globalMetrics=$globalMetrics, " +
            "defaultTextStyle=$defaultTextStyle, " +
            "editorTextStyle=$editorTextStyle, " +
            "consoleTextStyle=$consoleTextStyle, " +
            "contentColor=$contentColor, " +
            "colorPalette=$colorPalette, " +
            "iconData=$iconData, " +
            "grayFilterValues=$disabledAppearanceValues" +
            ")"
    }
}
