package org.jetbrains.jewel.ui.util

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import kotlin.math.roundToInt

/**
 * Converts a [java.awt.Color] to a RGBA formatted color `#RRGGBBAA` hex string; e.g., `#FFFFFF1A` (a translucent
 * white).
 */
public fun java.awt.Color.toRgbaHexString(): String {
    val r = Integer.toHexString(red)
    val g = Integer.toHexString(green)
    val b = Integer.toHexString(blue)

    return buildString {
        append('#')
        append(r.padStart(2, '0'))
        append(g.padStart(2, '0'))
        append(b.padStart(2, '0'))

        if (alpha < 255) {
            val a = Integer.toHexString(alpha)
            append(a.padStart(2, '0'))
        }
    }
}

/** Converts a [Color] to a RGBA formatted color `#RRGGBBAA` hex string; e.g., `#FFFFFF1A` (a translucent white). */
public fun Color.toRgbaHexString(): String {
    val r = Integer.toHexString((red * 255).roundToInt())
    val g = Integer.toHexString((green * 255).roundToInt())
    val b = Integer.toHexString((blue * 255).roundToInt())

    return buildString {
        append('#')
        append(r.padStart(2, '0'))
        append(g.padStart(2, '0'))
        append(b.padStart(2, '0'))

        if (alpha != 1.0f) {
            val a = Integer.toHexString((alpha * 255).roundToInt())
            append(a.padStart(2, '0'))
        }
    }
}

/** Converts a RGBA formatted color `#RRGGBBAA` hex string to a [Color]; e.g., `#FFFFFF1A` (a translucent white). */
public fun Color.Companion.fromRGBAHexStringOrNull(rgba: String): Color? =
    rgba
        .lowercase()
        .removePrefix("#")
        .let {
            when (it.length) {
                3 -> "ff${it[0]}${it[0]}${it[1]}${it[1]}${it[2]}${it[2]}"
                4 -> "${it[3]}${it[3]}${it[0]}${it[0]}${it[1]}${it[1]}${it[2]}${it[2]}"
                6 -> "ff$it"
                8 -> "${it[6]}${it[7]}${it[0]}${it[1]}${it[2]}${it[3]}${it[4]}${it[5]}"
                else -> null
            }
        }
        ?.toLongOrNull(radix = 16)
        ?.let { Color(it) }

/** Heuristically determines if the color can be thought of as "dark". */
public fun Color.isDark(): Boolean = (luminance() + 0.05) / 0.05 < 4.5
