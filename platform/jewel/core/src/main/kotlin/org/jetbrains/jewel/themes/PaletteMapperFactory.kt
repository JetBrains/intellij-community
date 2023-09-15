package org.jetbrains.jewel.themes

import androidx.compose.ui.graphics.Color
import org.jetbrains.jewel.PaletteMapper

abstract class PaletteMapperFactory {

    protected fun createInternal(
        iconColorPalette: Map<String, String>,
        keyPalette: Map<String, String>,
        themeColors: Map<String, Any>,
        isDark: Boolean,
    ): PaletteMapper {
        // This partially emulates what com.intellij.ide.ui.UITheme.loadFromJson does
        val ui = mutableMapOf<Color, Color>()
        val checkBoxes = mutableMapOf<Color, Color>()
        val trees = mutableMapOf<Color, Color>()

        for ((key, value) in iconColorPalette) {
            val map = selectMap(key, checkBoxes, trees, ui) ?: continue

            // If the value is one of the named colors in the theme, use that named color's value
            val namedColor = themeColors.get(value) as? String
            val resolvedValue = namedColor ?: value

            // If either the key or the resolved value aren't valid colors, ignore the entry
            val keyAsColor = resolveKeyColor(key, keyPalette, isDark) ?: continue
            val resolvedValueAsColor = resolvedValue.toColorOrNull() ?: continue

            // Save the new entry (oldColor -> newColor) in the map
            map[keyAsColor] = resolvedValueAsColor
        }

        return PaletteMapper(
            ui = PaletteMapper.Scope(ui),
            checkBoxes = PaletteMapper.Scope(checkBoxes),
            trees = PaletteMapper.Scope(trees),
        )
    }

    // See com.intellij.ide.ui.UITheme.toColorString
    private fun resolveKeyColor(key: String, keyPalette: Map<String, String>, isDark: Boolean): Color? {
        val darkKey = "$key.Dark"
        val resolvedKey = if (isDark && keyPalette.containsKey(darkKey)) darkKey else key
        return keyPalette[resolvedKey]?.toColorOrNull()
    }

    private fun selectMap(
        key: String,
        checkBoxes: MutableMap<Color, Color>,
        trees: MutableMap<Color, Color>,
        ui: MutableMap<Color, Color>,
    ) = when {
        key.startsWith("Checkbox.") -> checkBoxes
        key.startsWith("Tree.iconColor.") -> trees
        key.startsWith("Objects.") || key.startsWith("Actions.") || key.startsWith("#") -> ui
        else -> {
            logInfo("No PaletteMapperScope defined for key '$key'")
            null
        }
    }

    private fun String.toColorOrNull() =
        lowercase()
            .removePrefix("#")
            .removePrefix("0x")
            .let {
                when (it.length) {
                    3 -> "ff${it[0]}${it[0]}${it[1]}${it[1]}${it[2]}${it[2]}"
                    4 -> "${it[0]}${it[0]}${it[1]}${it[1]}${it[2]}${it[2]}${it[3]}${it[3]}"
                    6 -> "ff$it"
                    8 -> it
                    else -> null
                }
            }
            ?.toLongOrNull(radix = 16)
            ?.let { Color(it) }

    abstract fun logInfo(message: String)
}
