package org.jetbrains.jewel.foundation.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import org.jetbrains.jewel.foundation.GenerateDataFunctions

private val colorKeyRegex: Regex
    get() = "([a-z]+)(\\d+)".toRegex(RegexOption.IGNORE_CASE)

@Suppress("MemberVisibilityCanBePrivate")
@Immutable
@GenerateDataFunctions
public class ThemeColorPalette(
    public val gray: List<Color>,
    public val blue: List<Color>,
    public val green: List<Color>,
    public val red: List<Color>,
    public val yellow: List<Color>,
    public val orange: List<Color>,
    public val purple: List<Color>,
    public val teal: List<Color>,
    public val rawMap: Map<String, Color>,
) {
    @Deprecated("Use gray() instead", ReplaceWith("gray(index)")) public fun grey(index: Int): Color = gray(index)

    @Deprecated("Use grayOrNull() instead", ReplaceWith("grayOrNull(index)"))
    public fun greyOrNull(index: Int): Color? = grayOrNull(index)

    public fun gray(index: Int): Color = gray[index - 1]

    public fun grayOrNull(index: Int): Color? = gray.getOrNull(index - 1)

    public fun blue(index: Int): Color = blue[index - 1]

    public fun blueOrNull(index: Int): Color? = blue.getOrNull(index - 1)

    public fun green(index: Int): Color = green[index - 1]

    public fun greenOrNull(index: Int): Color? = green.getOrNull(index - 1)

    public fun red(index: Int): Color = red[index - 1]

    public fun redOrNull(index: Int): Color? = red.getOrNull(index - 1)

    public fun yellow(index: Int): Color = yellow[index - 1]

    public fun yellowOrNull(index: Int): Color? = yellow.getOrNull(index - 1)

    public fun orange(index: Int): Color = orange[index - 1]

    public fun orangeOrNull(index: Int): Color? = orange.getOrNull(index - 1)

    public fun purple(index: Int): Color = purple[index - 1]

    public fun purpleOrNull(index: Int): Color? = purple.getOrNull(index - 1)

    public fun teal(index: Int): Color = teal[index - 1]

    public fun tealOrNull(index: Int): Color? = teal.getOrNull(index - 1)

    public fun lookup(colorKey: String): Color? {
        val result = colorKeyRegex.matchEntire(colorKey.trim())
        val colorGroup = result?.groupValues?.get(1)?.lowercase()
        val colorIndex = result?.groupValues?.get(2)?.toIntOrNull()

        if (colorGroup == null || colorIndex == null) {
            return rawMap[colorKey]
        }

        return when (colorGroup) {
            "grey",
            "gray" -> gray(colorIndex)
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

    public companion object {
        public val Empty: ThemeColorPalette =
            ThemeColorPalette(
                gray = emptyList(),
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
