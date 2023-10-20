package org.jetbrains.jewel.intui.core

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import org.jetbrains.jewel.GenerateDataFunctions
import org.jetbrains.jewel.GlobalColors
import org.jetbrains.jewel.GlobalMetrics
import org.jetbrains.jewel.IntelliJThemeDefinition
import org.jetbrains.jewel.IntelliJThemeIconData

@Immutable
@GenerateDataFunctions
class IntUiThemeDefinition(
    override val isDark: Boolean,
    override val globalColors: GlobalColors,
    override val colorPalette: IntUiThemeColorPalette,
    override val iconData: IntelliJThemeIconData,
    override val globalMetrics: GlobalMetrics,
    override val defaultTextStyle: TextStyle,
    override val contentColor: Color,
) : IntelliJThemeDefinition
