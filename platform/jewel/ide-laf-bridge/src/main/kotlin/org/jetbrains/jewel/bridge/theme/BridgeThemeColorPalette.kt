package org.jetbrains.jewel.bridge.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.isSpecified
import com.intellij.openapi.diagnostic.Logger
import java.util.TreeMap
import org.jetbrains.jewel.bridge.toComposeColor
import org.jetbrains.jewel.foundation.theme.ThemeColorPalette

private val logger = Logger.getInstance("BridgeThemeColorPalette")

public val ThemeColorPalette.windowsPopupBorder: Color?
    get() = lookup("windowsPopupBorder")

public fun ThemeColorPalette.Companion.readFromLaF(): ThemeColorPalette {
    val gray = readPaletteColors("Gray")
    val blue = readPaletteColors("Blue")
    val green = readPaletteColors("Green")
    val red = readPaletteColors("Red")
    val yellow = readPaletteColors("Yellow")
    val orange = readPaletteColors("Orange")
    val purple = readPaletteColors("Purple")
    val teal = readPaletteColors("Teal")
    val windowsPopupBorder = readPaletteColor("windowsPopupBorder")

    val grayInt = gray.filterIntKeys()
    val blueInt = blue.filterIntKeys()
    val greenInt = green.filterIntKeys()
    val redInt = red.filterIntKeys()
    val yellowInt = yellow.filterIntKeys()
    val orangeInt = orange.filterIntKeys()
    val purpleInt = purple.filterIntKeys()
    val tealInt = teal.filterIntKeys()

    val rawMap = buildMap {
        putAll(gray)
        putAll(blue)
        putAll(green)
        putAll(red)
        putAll(yellow)
        putAll(orange)
        putAll(purple)
        putAll(teal)
        if (windowsPopupBorder.isSpecified) put("windowsPopupBorder", windowsPopupBorder)
    }

    return ThemeColorPalette(
        gray = grayInt.values.toList(),
        blue = blueInt.values.toList(),
        green = greenInt.values.toList(),
        red = redInt.values.toList(),
        yellow = yellowInt.values.toList(),
        orange = orangeInt.values.toList(),
        purple = purpleInt.values.toList(),
        teal = tealInt.values.toList(),
        rawMap = rawMap,
    )
}

private fun Map<String, Color>.filterIntKeys(): Map<String, Color> {
    val intMap = TreeMap<String, Color>()

    for ((key, value) in this) {
        val colorName = key.substringAfter("${ThemeColorPalette.PALETTE_KEY_PREFIX}.")
        val colorNameWithoutIndex = colorName.takeWhile { !it.isDigit() }
        val index = colorName.substring(colorNameWithoutIndex.length)

        if (index.toIntOrNull() != null) {
            intMap[key] = value
        }
    }
    return intMap
}

private fun readPaletteColors(colorName: String): Map<String, Color> {
    val defaults = uiDefaults
    val allKeys = defaults.keys
    val colorNameKeyPrefix = "${ThemeColorPalette.PALETTE_KEY_PREFIX}.$colorName"
    val colorNameKeyPrefixLength = colorNameKeyPrefix.length

    val keys =
        allKeys
            .asSequence()
            .filterIsInstance(String::class.java)
            .filter { it.startsWith(colorNameKeyPrefix) }
            .filter {
                val afterName = it.substring(colorNameKeyPrefixLength)
                // Match integer or fractional color
                afterName.matches("\\d+\\.\\d+".toRegex()) || afterName.toIntOrNull() != null
            }

    return buildMap {
        for (key in keys) {
            val value = defaults[key] as? java.awt.Color
            if (value == null) {
                logger.error("Unable to find color value for palette key '$key'")
                continue
            }

            put(key, value.toComposeColor())
        }
    }
}

private fun readPaletteColor(colorName: String): Color {
    val defaults = uiDefaults
    val colorNameKey = "${ThemeColorPalette.PALETTE_KEY_PREFIX}.$colorName"
    return (defaults[colorNameKey] as? java.awt.Color)?.toComposeColor() ?: Color.Unspecified
}
