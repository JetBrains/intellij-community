package org.jetbrains.jewel.ui.util

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import java.awt.Color as AwtColor
import kotlin.math.roundToInt

/**
 * Converts an AWT [`Color`][AwtColor] into a hex string with a `#RRGGBB(AA)` format.
 *
 * For example, a translucent white color will be converted to `#FFFFFF1A`. Note that the alpha component is only added
 * if the color is not fully opaque.
 */
@Deprecated(
    "Use the overload with default parameters instead.",
    replaceWith = ReplaceWith("toRgbaHexString(omitAlphaWhenFullyOpaque = true)"),
)
public fun AwtColor.toRgbaHexString(): String = toRgbaHexString(omitAlphaWhenFullyOpaque = true)

/**
 * Converts an AWT [`Color`][AwtColor] to a hex string in an RGBA format like `#(RR)GGBBAA`.
 *
 * For example, a 50% transparent red color is converted to `#FF000080`.
 *
 * By default, for fully opaque colors, the alpha component is omitted, resulting in an `#RRGGBB` format. For example,
 * [Color.Red] becomes `#FF0000`. You can force the inclusion of the alpha component for opaque colors by setting
 * [omitAlphaWhenFullyOpaque] to `false`, which would yield `#FF0000FF` for [Color.Red].
 *
 * The leading hash symbol can be removed by setting [includeHashSymbol] to `false`.
 *
 * @param includeHashSymbol If true, the returned string is prefixed with `#`. Defaults to `true`.
 * @param omitAlphaWhenFullyOpaque If true, the alpha component is omitted for fully opaque colors. Defaults to `true`.
 */
public fun AwtColor.toRgbaHexString(
    includeHashSymbol: Boolean = true,
    omitAlphaWhenFullyOpaque: Boolean = false,
): String {
    val r = Integer.toHexString(red)
    val g = Integer.toHexString(green)
    val b = Integer.toHexString(blue)

    return formatColorRgbaHexString(r, g, b, alpha, includeHashSymbol, omitAlphaWhenFullyOpaque)
}

/**
 * Converts a [Color] to a hex string with a `#RRGGBB(AA)` format.
 *
 * For example, a translucent white color will be converted to `#FFFFFF1A`. Note that the alpha component is only added
 * if the color is not fully opaque.
 */
@Deprecated(
    "Use the overload with default parameters instead.",
    replaceWith = ReplaceWith("toRgbaHexString(omitAlphaWhenFullyOpaque = true)"),
)
public fun Color.toRgbaHexString(): String = toRgbaHexString(omitAlphaWhenFullyOpaque = true)

/**
 * Converts a [Color] to a hex string in an RGBA format like `#RRGGBB(AA)`.
 *
 * For example, a 50% transparent red color is converted to `#FF000080`.
 *
 * By default, for fully opaque colors, the alpha component is omitted, resulting in an `#RRGGBB` format. For example,
 * [Color.Red] becomes `#FF0000`. You can force the inclusion of the alpha component for opaque colors by setting
 * [omitAlphaWhenFullyOpaque] to `false`, which would yield `#FF0000FF` for [Color.Red].
 *
 * The leading hash symbol can be removed by setting [includeHashSymbol] to `false`.
 *
 * @param includeHashSymbol If true, the returned string is prefixed with `#`. Defaults to `true`.
 * @param omitAlphaWhenFullyOpaque If true, the alpha component is omitted for fully opaque colors. Defaults to `true`.
 */
public fun Color.toRgbaHexString(includeHashSymbol: Boolean = true, omitAlphaWhenFullyOpaque: Boolean = false): String {
    val r = Integer.toHexString((red * 255).roundToInt())
    val g = Integer.toHexString((green * 255).roundToInt())
    val b = Integer.toHexString((blue * 255).roundToInt())
    val intAlpha = (alpha * 255).roundToInt()

    return formatColorRgbaHexString(r, g, b, intAlpha, includeHashSymbol, omitAlphaWhenFullyOpaque)
}

private fun formatColorRgbaHexString(
    rString: String,
    gString: String,
    bString: String,
    alphaInt: Int,
    includeHashSymbol: Boolean,
    omitAlphaWhenFullyOpaque: Boolean,
): String = buildString {
    if (includeHashSymbol) append('#')

    append(rString.padStart(2, '0'))
    append(gString.padStart(2, '0'))
    append(bString.padStart(2, '0'))

    if (alphaInt < 255 || !omitAlphaWhenFullyOpaque) {
        val a = Integer.toHexString(alphaInt)
        append(a.padStart(2, '0'))
    }
}

/**
 * Converts a [Color] to a hex string in an ARGB format like `#(AA)RRGGBB`.
 *
 * For example, a 50% transparent red color is converted to `#80FF0000`.
 *
 * By default, for fully opaque colors, the alpha component is omitted, resulting in an `#RRGGBB` format. For example,
 * [Color.Red] becomes `#FF0000`. You can force the inclusion of the alpha component for opaque colors by setting
 * [omitAlphaWhenFullyOpaque] to `false`, which would yield `#FFFF0000` for [Color.Red].
 *
 * The leading hash symbol can be removed by setting [includeHashSymbol] to `false`.
 *
 * @param includeHashSymbol If true, the returned string is prefixed with `#`. Defaults to `true`.
 * @param omitAlphaWhenFullyOpaque If true, the alpha component is omitted for fully opaque colors. Defaults to `true`.
 */
public fun Color.toArgbHexString(includeHashSymbol: Boolean = true, omitAlphaWhenFullyOpaque: Boolean = false): String {
    val r = Integer.toHexString((red * 255).roundToInt())
    val g = Integer.toHexString((green * 255).roundToInt())
    val b = Integer.toHexString((blue * 255).roundToInt())
    val intAlpha = (alpha * 255).roundToInt()

    return formatColorArgbHexString(r, g, b, intAlpha, includeHashSymbol, omitAlphaWhenFullyOpaque)
}

/**
 * Converts an AWT [`Color`][AwtColor] to a hex string in an ARGB format like `#(AA)RRGGBB`.
 *
 * For example, a 50% transparent red color is converted to `#80FF0000`.
 *
 * By default, for fully opaque colors, the alpha component is omitted, resulting in an `#RRGGBB` format. For example,
 * [AwtColor.RED] becomes `#FF0000`. You can force the inclusion of the alpha component for opaque colors by setting
 * [omitAlphaWhenFullyOpaque] to `false`, which would yield `#FFFF0000` for [AwtColor.RED].
 *
 * The leading hash symbol can be removed by setting [includeHashSymbol] to `false`.
 *
 * @param includeHashSymbol If true, the returned string is prefixed with `#`. Defaults to `true`.
 * @param omitAlphaWhenFullyOpaque If true, the alpha component is omitted for fully opaque colors. Defaults to `true`.
 */
public fun AwtColor.toArgbHexString(
    includeHashSymbol: Boolean = true,
    omitAlphaWhenFullyOpaque: Boolean = false,
): String {
    val r = Integer.toHexString(red)
    val g = Integer.toHexString(green)
    val b = Integer.toHexString(blue)

    return formatColorArgbHexString(r, g, b, alpha, includeHashSymbol, omitAlphaWhenFullyOpaque)
}

private fun formatColorArgbHexString(
    rString: String,
    gString: String,
    bString: String,
    alphaInt: Int,
    includeHashSymbol: Boolean,
    omitAlphaWhenFullyOpaque: Boolean,
): String = buildString {
    if (includeHashSymbol) append('#')

    if (alphaInt < 255 || !omitAlphaWhenFullyOpaque) {
        val a = Integer.toHexString(alphaInt)
        append(a.padStart(2, '0'))
    }

    append(rString.padStart(2, '0'))
    append(gString.padStart(2, '0'))
    append(bString.padStart(2, '0'))
}

/**
 * Converts a hex string in RGBA format to a [Color].
 *
 * This function supports the following formats, with or without a leading `#`:
 * - `RGB` (e.g., `F00` for opaque red)
 * - `RGBA` (e.g., `F00A` for a red with ~66% alpha)
 * - `RRGGBB` (e.g., `FF0000` for opaque red)
 * - `RRGGBBAA` (e.g., `FF0000AA` for a red with ~66% alpha)
 *
 * Note that the alpha component is placed at the end of the string. Returns `null` if the hex string is not a valid
 * color representation.
 */
@Deprecated("Use fromRgbaHexStringOrNull() instead.", replaceWith = ReplaceWith("fromRgbaHexStringOrNull(rgba)"))
public fun Color.Companion.fromRGBAHexStringOrNull(rgba: String): Color? = fromRgbaHexStringOrNull(rgba)

/**
 * Converts a hex string in RGBA format to a [Color].
 *
 * This function supports the following formats, with or without a leading `#`:
 * - `RGB` (e.g., `F00` for opaque red)
 * - `RGBA` (e.g., `F00A` for a red with ~66% alpha)
 * - `RRGGBB` (e.g., `FF0000` for opaque red)
 * - `RRGGBBAA` (e.g., `FF0000AA` for a red with ~66% alpha)
 *
 * Note that the alpha component is placed at the end of the string. Returns `null` if the hex string is not a valid
 * color representation.
 */
public fun Color.Companion.fromRgbaHexStringOrNull(rgba: String): Color? =
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

/**
 * Converts a hex string in ARGB format to a [Color].
 *
 * This function supports the following formats, with or without a leading `#`:
 * - `RGB` (e.g., `F00` for opaque red)
 * - `ARGB` (e.g., `AF00` for a red with ~66% alpha)
 * - `RRGGBB` (e.g., `FF0000` for opaque red)
 * - `AARRGGBB` (e.g., `AAFF0000` for a red with ~66% alpha)
 *
 * Note that the alpha component is placed at the beginning of the string for formats with alpha. Returns `null` if the
 * hex string is not a valid color representation.
 */
public fun Color.Companion.fromArgbHexStringOrNull(argb: String): Color? =
    argb
        .lowercase()
        .removePrefix("#")
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

/**
 * Heuristically determines if the color is "dark".
 *
 * This is useful for accessibility purposes, for example to decide whether to use a light or dark text on a colored
 * background.
 *
 * The formula is borrowed from the WCAG. See https://www.w3.org/TR/WCAG20/#relativeluminancedef
 */
public fun Color.isDark(): Boolean = (luminance() + 0.05) / 0.05 < 4.5
