package org.jetbrains.jewel.bridge

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import com.intellij.openapi.diagnostic.Logger
import org.jetbrains.jewel.themes.intui.core.IntUiThemeColorPalette

private val logger = Logger.getInstance("BridgeThemeColorPalette")

@Immutable
class BridgeThemeColorPalette(
    private val grey: List<Color>,
    private val blue: List<Color>,
    private val green: List<Color>,
    private val red: List<Color>,
    private val yellow: List<Color>,
    private val orange: List<Color>,
    private val purple: List<Color>,
    private val teal: List<Color>,
) : IntUiThemeColorPalette {

    override fun grey(): List<Color> = grey

    override fun grey(index: Int): Color = grey[index - 1]

    override fun blue(): List<Color> = blue

    override fun blue(index: Int): Color = blue[index - 1]

    override fun green(): List<Color> = green

    override fun green(index: Int): Color = green[index - 1]

    override fun red(): List<Color> = red

    override fun red(index: Int): Color = red[index - 1]

    override fun yellow(): List<Color> = yellow

    override fun yellow(index: Int): Color = yellow[index - 1]

    override fun orange(): List<Color> = orange

    override fun orange(index: Int): Color = orange[index - 1]

    override fun purple(): List<Color> = purple

    override fun purple(index: Int): Color = purple[index - 1]

    override fun teal(): List<Color> = teal

    override fun teal(index: Int): Color = teal[index - 1]

    companion object {

        fun readFromLaF() = BridgeThemeColorPalette(
            grey = readPaletteColors("Grey"),
            blue = readPaletteColors("Blue"),
            green = readPaletteColors("Green"),
            red = readPaletteColors("Red"),
            yellow = readPaletteColors("Yellow"),
            orange = readPaletteColors("Orange"),
            purple = readPaletteColors("Purple"),
            teal = readPaletteColors("Teal"),
        )

        private fun readPaletteColors(colorName: String): List<Color> {
            val defaults = uiDefaults
            val allKeys = defaults.keys
            val colorNameKeyPrefix = "ColorPalette.$colorName"
            val colorNameKeyPrefixLength = colorNameKeyPrefix.length

            val lastColorIndex = allKeys.asSequence()
                .filterIsInstance(String::class.java)
                .filter { it.startsWith(colorNameKeyPrefix) }
                .mapNotNull {
                    val afterName = it.substring(colorNameKeyPrefixLength)
                    afterName.toIntOrNull()
                }
                .maxOrNull() ?: return emptyList()

            return buildList {
                for (i in 1..lastColorIndex) {
                    val value = defaults["$colorNameKeyPrefix$i"] as? java.awt.Color
                    if (value == null) {
                        logger.error("Unable to find color value for palette key '$colorNameKeyPrefix$i'")
                        continue
                    }

                    add(value.toComposeColor())
                }
            }
        }
    }
}
