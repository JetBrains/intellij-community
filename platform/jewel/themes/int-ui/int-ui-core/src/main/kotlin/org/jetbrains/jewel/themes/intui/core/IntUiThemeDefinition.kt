package org.jetbrains.jewel.themes.intui.core

import androidx.compose.ui.text.TextStyle
import org.jetbrains.jewel.IntelliJThemeDefinition
import org.jetbrains.jewel.ThemeColors
import org.jetbrains.jewel.ThemeMetrics

data class IntUiThemeDefinition(
    override val isDark: Boolean,
    override val colors: ThemeColors,
    val palette: IntelliJThemeColorPalette,
    val icons: IntelliJThemeIcons,
    override val metrics: ThemeMetrics,
    override val defaultTextStyle: TextStyle,
) : IntelliJThemeDefinition
