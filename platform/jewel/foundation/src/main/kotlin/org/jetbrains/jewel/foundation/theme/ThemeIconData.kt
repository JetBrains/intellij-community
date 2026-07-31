package org.jetbrains.jewel.foundation.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color

/** Holds theme-specific icon path overrides and color palette mappings used to tint and remap SVG icons. */
@Immutable
public class ThemeIconData(
    /** Maps original icon paths to replacement icon paths for this theme. */
    public val iconOverrides: Map<String, String>,
    /** Maps color hex strings to replacement color hex strings (or null to leave unchanged) for icon tinting. */
    public val colorPalette: Map<String, String?>,
    /** Maps color hex strings to replacement ARGB int values used for selected icon tinting. */
    public val selectionColorPalette: Map<String, Int>,
) {
    /**
     * Converts [selectionColorPalette] into a typed [Map] from source [Color] (parsed from each hex-string key) to
     * replacement [Color] (built from each ARGB int value). Entries whose key string cannot be parsed as a color are
     * silently dropped.
     */
    public fun selectionColorMapping(): Map<Color, Color> =
        selectionColorPalette
            .mapNotNull { (key, value) ->
                val keyColor = key.toColorOrNull() ?: return@mapNotNull null
                val valueColor = Color(value)
                keyColor to valueColor
            }
            .toMap()

    /** Provides the [Empty] instance representing a theme with no icon customizations. */
    public companion object {
        public val Empty: ThemeIconData =
            ThemeIconData(iconOverrides = emptyMap(), colorPalette = emptyMap(), selectionColorPalette = emptyMap())
    }
}

internal fun String.toColorOrNull() =
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
