package org.jetbrains.jewel.bridge.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.isSpecified
import com.intellij.openapi.diagnostic.Logger
import com.intellij.ui.IslandsState
import java.util.TreeMap
import javax.swing.UIDefaults
import org.jetbrains.jewel.bridge.toComposeColor
import org.jetbrains.jewel.foundation.theme.ThemeColorPalette

private val logger: Logger = Logger.getInstance("BridgeThemeColorPalette")

/** The border color used for Windows-style popups, or null if not defined in the current LaF. */
public val ThemeColorPalette.windowsPopupBorder: Color?
    get() = lookup("windowsPopupBorder")

/**
 * Reads [ThemeColorPalette] from the current IntelliJ LaF by extracting named color-palette entries from UIDefaults.
 */
public fun ThemeColorPalette.Companion.readFromLaF(): ThemeColorPalette {
    val isIslands = IslandsState.isEnabled()
    val gray = readPaletteColors("Gray", isIslands)
    val blue = readPaletteColors("Blue", isIslands)
    val green = readPaletteColors("Green", isIslands)
    val red = readPaletteColors("Red", isIslands)
    val yellow = readPaletteColors("Yellow", isIslands)
    val orange = readPaletteColors("Orange", isIslands)
    val purple = readPaletteColors("Purple", isIslands)
    val teal = readPaletteColors("Teal", isIslands)
    val windowsPopupBorder = readPaletteColor("windowsPopupBorder")

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
        gray = gray.values.toList(),
        blue = blue.values.toList(),
        green = green.values.toList(),
        red = red.values.toList(),
        yellow = yellow.values.toList(),
        orange = orange.values.toList(),
        purple = purple.values.toList(),
        teal = teal.values.toList(),
        rawMap = rawMap,
        isIslands = isIslands,
    )
}

private fun readPaletteColors(colorName: String, isIslands: Boolean): Map<String, Color> {
    val defaults: UIDefaults = uiDefaults
    val allKeys: Set<Any> = defaults.keys
    val colorNameKeyPrefix = if (isIslands) "ColorPalette.${colorName.lowercase()}-" else "ColorPalette.$colorName"
    val colorNameKeyPrefixLength = colorNameKeyPrefix.length

    val lastColorIndex =
        allKeys
            .filterIsInstance<String>()
            .filter { it.startsWith(colorNameKeyPrefix) }
            .mapNotNull {
                val afterName = it.substring(colorNameKeyPrefixLength)
                afterName.toIntOrNull()
            }
            .maxOrNull()
    if (lastColorIndex == null) return TreeMap()

    val indices =
        if (isIslands) {
            (10..lastColorIndex step 10)
        } else {
            (1..lastColorIndex)
        }

    return buildMap {
        for (i in indices) {
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
    val defaults: UIDefaults = uiDefaults
    val colorNameKey = "ColorPalette.$colorName"
    return (defaults[colorNameKey] as? java.awt.Color)?.toComposeColor() ?: Color.Unspecified
}
