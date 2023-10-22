package org.jetbrains.jewel.foundation.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import org.jetbrains.jewel.foundation.GenerateDataFunctions
import org.jetbrains.jewel.foundation.GlobalColors
import org.jetbrains.jewel.foundation.GlobalMetrics

@Immutable
@GenerateDataFunctions
class ThemeDefinition(
    val isDark: Boolean,
    val globalColors: GlobalColors,
    val globalMetrics: GlobalMetrics,
    val defaultTextStyle: TextStyle,
    val contentColor: Color,
    val colorPalette: ThemeColorPalette,
    val iconData: ThemeIconData,
)
