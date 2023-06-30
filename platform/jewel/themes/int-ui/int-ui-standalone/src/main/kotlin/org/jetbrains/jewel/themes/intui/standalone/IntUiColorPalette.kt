package org.jetbrains.jewel.themes.intui.standalone

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

interface IntUiColorPalette {

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

    fun isLight(): Boolean
}

internal val LocalIntUiPalette = staticCompositionLocalOf<IntUiColorPalette> {
    error("No IntUiPalettes provided")
}
