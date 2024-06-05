package org.jetbrains.jewel.foundation.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
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
)
