package org.jetbrains.jewel.themes

import androidx.compose.ui.graphics.Color
import org.jetbrains.jewel.IntelliJThemeColorPalette
import org.jetbrains.jewel.IntelliJThemeIconData
import org.jetbrains.jewel.PaletteMapper

object PaletteMapperFactory {

    // Extracted from com.intellij.ide.ui.UITheme#colorPalette
    private val colorsToMap = mapOf(
        "Actions.Red" to "#DB5860",
        "Actions.Red.Dark" to "#C75450",
        "Actions.Yellow" to "#EDA200",
        "Actions.Yellow.Dark" to "#F0A732",
        "Actions.Green" to "#59A869",
        "Actions.Green.Dark" to "#499C54",
        "Actions.Blue" to "#389FD6",
        "Actions.Blue.Dark" to "#3592C4",
        "Actions.Grey" to "#6E6E6E",
        "Actions.Grey.Dark" to "#AFB1B3",
        "Actions.GreyInline" to "#7F8B91",
        "Actions.GreyInline.Dark" to "#7F8B91",
        "Objects.Grey" to "#9AA7B0",
        "Objects.Blue" to "#40B6E0",
        "Objects.Green" to "#62B543",
        "Objects.Yellow" to "#F4AF3D",
        "Objects.YellowDark" to "#D9A343",
        "Objects.Purple" to "#B99BF8",
        "Objects.Pink" to "#F98B9E",
        "Objects.Red" to "#F26522",
        "Objects.RedStatus" to "#E05555",
        "Objects.GreenAndroid" to "#3DDC84",
        "Objects.BlackText" to "#231F20",
        "Checkbox.Background.Default" to "#FFFFFF",
        "Checkbox.Background.Default.Dark" to "#43494A",
        "Checkbox.Background.Disabled" to "#F2F2F2",
        "Checkbox.Background.Disabled.Dark" to "#3C3F41",
        "Checkbox.Border.Default" to "#b0b0b0",
        "Checkbox.Border.Default.Dark" to "#6B6B6B",
        "Checkbox.Border.Disabled" to "#BDBDBD",
        "Checkbox.Border.Disabled.Dark" to "#545556",
        "Checkbox.Focus.Thin.Default" to "#7B9FC7",
        "Checkbox.Focus.Thin.Default.Dark" to "#466D94",
        "Checkbox.Focus.Wide" to "#97C3F3",
        "Checkbox.Focus.Wide.Dark" to "#3D6185",
        "Checkbox.Foreground.Disabled" to "#ABABAB",
        "Checkbox.Foreground.Disabled.Dark" to "#606060",
        "Checkbox.Background.Selected" to "#4F9EE3",
        "Checkbox.Background.Selected.Dark" to "#43494A",
        "Checkbox.Border.Selected" to "#4B97D9",
        "Checkbox.Border.Selected.Dark" to "#6B6B6B",
        "Checkbox.Foreground.Selected" to "#FEFEFE",
        "Checkbox.Foreground.Selected.Dark" to "#A7A7A7",
        "Checkbox.Focus.Thin.Selected" to "#ACCFF7",
        "Checkbox.Focus.Thin.Selected.Dark" to "#466D94",
        "Tree.iconColor" to "#808080",
        "Tree.iconColor.Dark" to "#AFB1B3"
    )

    fun create(
        isDark: Boolean,
        iconData: IntelliJThemeIconData,
        colorPalette: IntelliJThemeColorPalette,
    ): PaletteMapper {
        val overrides = computeOverrides(isDark, iconData.colorPalette, colorPalette)
        return PaletteMapper(overrides)
    }

    // 1. Load the icons.ColorPalette map from the theme JSON, if it exists, and for each key:
    // 2. Resolve the hex value by looking up the ColorPalette key
    //    * Append ".Dark" to the key if in dark theme
    //    * Read the corresponding value from the UITheme#colorPalette map (the "oldColor" hex value)
    // 3. Read the "newColor" hex value corresponding to the key from the theme's icons.ColorPalette map
    // 4. Resolve (if needed) the newColor
    //    * The "newColor" in the theme may be a theme color palette key, that needs to be resolved to a hex value
    //    * Note down the alpha value, if any is specified (that is, if the value is in the #AARRGGBB format)
    // 6. Write a new entry oldColor -> newColor, bringing over the original alpha into the newColor (if any)
    private fun computeOverrides(
        isDark: Boolean,
        iconColorMap: Map<String, String>,
        colorPalette: IntelliJThemeColorPalette,
    ) =
        buildMap {
            for (colorOverride in iconColorMap) {
                val key = colorHexForKey(colorOverride.key, isDark)
                val overrideValue = colorOverride.value
                val newColor = overrideValue.takeIf { it.startsWith("#") }?.toColorOrNull()
                    ?: colorPalette.lookup(overrideValue)
                    ?: continue
                val oldColor = key.toColorOrNull() ?: continue
                put(oldColor, newColor)
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

    private fun colorHexForKey(key: String, isDark: Boolean): String {
        val resolvedColor = if (isDark) {
            colorsToMap["$key.Dark"] ?: colorsToMap[key]
        } else {
            colorsToMap[key]
        }
        return (resolvedColor ?: key).lowercase()
    }
}
