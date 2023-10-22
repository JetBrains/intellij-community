package org.jetbrains.jewel.foundation.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import org.jetbrains.jewel.foundation.GenerateDataFunctions

private val colorKeyRegex: Regex
    get() = "([a-z]+)(\\d+)".toRegex(RegexOption.IGNORE_CASE)

@Immutable
@GenerateDataFunctions
class ThemeColorPalette(
    val grey: List<Color>,
    val blue: List<Color>,
    val green: List<Color>,
    val red: List<Color>,
    val yellow: List<Color>,
    val orange: List<Color>,
    val purple: List<Color>,
    val teal: List<Color>,
    val rawMap: Map<String, Color>,
) {

    fun grey(index: Int): Color = grey[index - 1]

    fun greyOrNull(index: Int): Color? = grey.getOrNull(index - 1)

    fun blue(index: Int): Color = blue[index - 1]

    fun blueOrNull(index: Int): Color? = blue.getOrNull(index - 1)

    fun green(index: Int): Color = green[index - 1]

    fun greenOrNull(index: Int): Color? = green.getOrNull(index - 1)

    fun red(index: Int): Color = red[index - 1]

    fun redOrNull(index: Int): Color? = red.getOrNull(index - 1)

    fun yellow(index: Int): Color = yellow[index - 1]

    fun yellowOrNull(index: Int): Color? = yellow.getOrNull(index - 1)

    fun orange(index: Int): Color = orange[index - 1]
    fun orangeOrNull(index: Int): Color? = orange.getOrNull(index - 1)

    fun purple(index: Int): Color = purple[index - 1]

    fun purpleOrNull(index: Int): Color? = purple.getOrNull(index - 1)

    fun teal(index: Int): Color = teal[index - 1]

    fun tealOrNull(index: Int): Color? = teal.getOrNull(index - 1)

    fun lookup(colorKey: String): Color? {
        val result = colorKeyRegex.matchEntire(colorKey.trim())
        val colorGroup = result?.groupValues?.get(1)?.lowercase()
        val colorIndex = result?.groupValues?.get(2)?.toIntOrNull()

        if (colorGroup == null || colorIndex == null) {
            return rawMap[colorKey]
        }

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

    companion object {

        val Empty = ThemeColorPalette(
            grey = emptyList(),
            blue = emptyList(),
            green = emptyList(),
            red = emptyList(),
            yellow = emptyList(),
            orange = emptyList(),
            purple = emptyList(),
            teal = emptyList(),
            rawMap = emptyMap(),
        )
    }
}
