package org.jetbrains.jewel.bridge

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.isSpecified
import com.intellij.openapi.diagnostic.Logger
import org.jetbrains.jewel.themes.intui.core.IntUiThemeColorPalette
import java.util.TreeMap

private val logger = Logger.getInstance("BridgeThemeColorPalette")

@Immutable
class BridgeThemeColorPalette private constructor(
    override val rawMap: Map<String, Color>,
    private val grey: List<Color>,
    private val blue: List<Color>,
    private val green: List<Color>,
    private val red: List<Color>,
    private val yellow: List<Color>,
    private val orange: List<Color>,
    private val purple: List<Color>,
    private val teal: List<Color>,
    private val windowsPopupBorder: Color,
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

        fun readFromLaF(): BridgeThemeColorPalette {
            val grey = readPaletteColors("Grey")
            val blue = readPaletteColors("Blue")
            val green = readPaletteColors("Green")
            val red = readPaletteColors("Red")
            val yellow = readPaletteColors("Yellow")
            val orange = readPaletteColors("Orange")
            val purple = readPaletteColors("Purple")
            val teal = readPaletteColors("Teal")
            val windowsPopupBorder = readPaletteColor("windowsPopupBorder")

            val rawMap = buildMap<String, Color> {
                putAll(grey)
                putAll(blue)
                putAll(green)
                putAll(red)
                putAll(yellow)
                putAll(orange)
                putAll(purple)
                putAll(teal)
                if (windowsPopupBorder.isSpecified) put("windowsPopupBorder", windowsPopupBorder)
            }

            return BridgeThemeColorPalette(
                grey = grey.values.toList(),
                blue = blue.values.toList(),
                green = green.values.toList(),
                red = red.values.toList(),
                yellow = yellow.values.toList(),
                orange = orange.values.toList(),
                purple = purple.values.toList(),
                teal = teal.values.toList(),
                windowsPopupBorder = windowsPopupBorder,
                rawMap = rawMap,
            )
        }

        private fun readPaletteColors(colorName: String): Map<String, Color> {
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
                .maxOrNull() ?: return TreeMap()

            return buildMap {
                for (i in 1..lastColorIndex) {
                    val key = "$colorNameKeyPrefix$i"
                    val value = defaults[key] as? java.awt.Color
                    if (value == null) {
                        logger.error("Unable to find color value for palette key '$colorNameKeyPrefix$i'")
                        continue
                    }

                    put(key, value.toComposeColor())
                }
            }
        }

        private fun readPaletteColor(colorName: String): Color {
            val defaults = uiDefaults
            val colorNameKey = "ColorPalette.$colorName"
            return (defaults[colorNameKey] as? java.awt.Color)
                ?.toComposeColor() ?: Color.Unspecified
        }
    }
}
