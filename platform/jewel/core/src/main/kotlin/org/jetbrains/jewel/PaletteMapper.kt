package org.jetbrains.jewel

import androidx.compose.ui.graphics.Color

class PaletteMapper(val colorOverrides: Map<Color, Color>) {

    fun mapColor(originalColor: Color): Color =
        mapColorOrNull(originalColor) ?: originalColor

    fun mapColorOrNull(originalColor: Color): Color? {
        if (colorOverrides.isEmpty()) return null

        return colorOverrides[originalColor]
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as PaletteMapper

        return colorOverrides == other.colorOverrides
    }

    override fun hashCode(): Int = colorOverrides.hashCode()

    override fun toString() = "PaletteMapper(colorOverrides=$colorOverrides)"
}
