package org.jetbrains.jewel.themes.intui.core

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import org.jetbrains.jewel.themes.intui.core.theme.IntUiLightTheme

interface IntelliJThemeColorPalette {

    fun grey(): List<Color>

    fun grey(index: Int): Color

    fun blue(): List<Color>

    fun blue(index: Int): Color

    fun green(): List<Color>

    fun green(index: Int): Color

    fun red(): List<Color>

    fun red(index: Int): Color

    fun yellow(): List<Color>

    fun yellow(index: Int): Color

    fun orange(): List<Color>

    fun orange(index: Int): Color

    fun purple(): List<Color>

    fun purple(index: Int): Color

    fun teal(): List<Color>

    fun teal(index: Int): Color
}

val LocalIntUiPalette = staticCompositionLocalOf {
    IntUiLightTheme.colors
}

val LocalIntUiIcons = staticCompositionLocalOf {
    IntUiLightTheme.icons
}
