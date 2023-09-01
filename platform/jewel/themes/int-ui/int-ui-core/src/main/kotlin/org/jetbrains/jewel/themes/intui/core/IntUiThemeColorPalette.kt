package org.jetbrains.jewel.themes.intui.core

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import org.jetbrains.jewel.IntelliJThemeColorPalette

@Immutable
interface IntUiThemeColorPalette : IntelliJThemeColorPalette {

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

    override fun lookup(colorKey: String): Color? {
        val result = colorKeyRegex.matchEntire(colorKey.trim()) ?: return null
        val colorGroup = result.groupValues[1].lowercase()
        val colorIndex = result.groupValues[2].toIntOrNull() ?: return null

        return when (colorGroup) {
            "grey" -> grey(colorIndex)
            "blue" -> blue(colorIndex)
            "green" -> green(colorIndex)
            "red" -> red(colorIndex)
            "yellow" -> yellow(colorIndex)
            "orange" -> orange(colorIndex)
            "purple" -> purple(colorIndex)
            "teal" -> teal(colorIndex)
            else -> null
        }
    }
}

internal object EmptyIntUiThemeColorPalette : IntUiThemeColorPalette {

    override fun grey(): List<Color> = emptyList()

    override fun grey(index: Int): Color = Color.Unspecified

    override fun blue(): List<Color> = emptyList()

    override fun blue(index: Int): Color = Color.Unspecified

    override fun green(): List<Color> = emptyList()

    override fun green(index: Int): Color = Color.Unspecified

    override fun red(): List<Color> = emptyList()

    override fun red(index: Int): Color = Color.Unspecified

    override fun yellow(): List<Color> = emptyList()

    override fun yellow(index: Int): Color = Color.Unspecified

    override fun orange(): List<Color> = emptyList()

    override fun orange(index: Int): Color = Color.Unspecified

    override fun purple(): List<Color> = emptyList()

    override fun purple(index: Int): Color = Color.Unspecified

    override fun teal(): List<Color> = emptyList()

    override fun teal(index: Int): Color = Color.Unspecified

    override fun lookup(colorKey: String): Color? = null
}

private val colorKeyRegex: Regex
    get() = "([a-z]+)(\\d+)".toRegex(RegexOption.IGNORE_CASE)
