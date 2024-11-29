package org.jetbrains.jewel.foundation.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color

@Immutable
public class ThemeIconData(
    public val iconOverrides: Map<String, String>,
    public val colorPalette: Map<String, String?>,
    public val selectionColorPalette: Map<String, Int>,
) {
    public fun selectionColorMapping(): Map<Color, Color> =
        selectionColorPalette
            .mapNotNull { (key, value) ->
                val keyColor = key.toColorOrNull() ?: return@mapNotNull null
                val valueColor = Color(value)
                keyColor to valueColor
            }
            .toMap()

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
