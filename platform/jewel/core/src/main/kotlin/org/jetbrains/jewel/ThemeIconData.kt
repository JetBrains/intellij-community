package org.jetbrains.jewel

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color

@Immutable
class ThemeIconData(
    val iconOverrides: Map<String, String>,
    val colorPalette: Map<String, String?>,
    val selectionColorPalette: Map<String, Int>,
) {

    fun selectionColorMapping() =
        selectionColorPalette.mapNotNull { (key, value) ->
            val keyColor = key.toColorOrNull() ?: return@mapNotNull null
            val valueColor = Color(value)
            keyColor to valueColor
        }.toMap()

    companion object {

        val Empty = ThemeIconData(
            iconOverrides = emptyMap(),
            colorPalette = emptyMap(),
            selectionColorPalette = emptyMap(),
        )
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
