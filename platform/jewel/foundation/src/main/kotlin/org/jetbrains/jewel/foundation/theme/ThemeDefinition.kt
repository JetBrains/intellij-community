package org.jetbrains.jewel.foundation.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import org.jetbrains.jewel.foundation.DisabledAppearanceValues
import org.jetbrains.jewel.foundation.GenerateDataFunctions
import org.jetbrains.jewel.foundation.GlobalColors
import org.jetbrains.jewel.foundation.GlobalMetrics

/**
 * Defines all visual properties of a Jewel theme: its name, dark/light mode, global colors and metrics, text styles,
 * content color, color palette, icon data, and disabled appearance values.
 */
@Immutable
@GenerateDataFunctions
public class ThemeDefinition(
    /** The unique name identifying this theme. */
    public val name: String,
    /** Whether this theme is a dark theme. */
    public val isDark: Boolean,
    /** The global colors shared across all components in this theme. */
    public val globalColors: GlobalColors,
    /** The global metrics (sizes, spacings) shared across all components in this theme. */
    public val globalMetrics: GlobalMetrics,
    /** The default text style used for body content. */
    public val defaultTextStyle: TextStyle,
    /** The text style used for editor content. */
    public val editorTextStyle: TextStyle,
    /** The text style used for console/terminal content. */
    public val consoleTextStyle: TextStyle,
    /** The default foreground color for content rendered with this theme. */
    public val contentColor: Color,
    /** The color palette providing semantic and raw color mappings for this theme. */
    public val colorPalette: ThemeColorPalette,
    /** The icon data providing icon overrides and mappings for this theme. */
    public val iconData: ThemeIconData,
    /** The values controlling the appearance of disabled components. */
    public val disabledAppearanceValues: DisabledAppearanceValues,
) {
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
