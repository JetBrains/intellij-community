package org.jetbrains.jewel

import androidx.compose.runtime.Immutable
import androidx.compose.ui.text.TextStyle

@Immutable
interface IntelliJThemeDefinition {

    val isDark: Boolean
    val colors: ThemeColors
    val metrics: ThemeMetrics
    val defaultTextStyle: TextStyle
}
