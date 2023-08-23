package org.jetbrains.jewel.themes.intui.core

import androidx.compose.ui.text.TextStyle
import org.jetbrains.jewel.GlobalColors
import org.jetbrains.jewel.GlobalMetrics
import org.jetbrains.jewel.IntelliJThemeDefinition
import org.jetbrains.jewel.IntelliJThemeIconData

data class IntUiThemeDefinition(
    override val isDark: Boolean,
    override val globalColors: GlobalColors,
    override val colorPalette: IntUiThemeColorPalette,
    override val iconData: IntelliJThemeIconData,
    override val metrics: GlobalMetrics,
    override val defaultTextStyle: TextStyle,
) : IntelliJThemeDefinition
