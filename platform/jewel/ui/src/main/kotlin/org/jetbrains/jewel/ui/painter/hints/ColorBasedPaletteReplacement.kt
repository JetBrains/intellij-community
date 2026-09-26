package org.jetbrains.jewel.ui.painter.hints

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import kotlin.math.roundToInt
import org.jetbrains.jewel.foundation.GenerateDataFunctions
import org.jetbrains.jewel.ui.painter.PainterHint
import org.jetbrains.jewel.ui.painter.PainterProviderScope
import org.jetbrains.jewel.ui.painter.PainterSvgPatchHint
import org.jetbrains.jewel.ui.util.toRgbaHexString
import org.w3c.dom.Element

@Immutable
@GenerateDataFunctions
private class ColorBasedReplacementPainterSvgPatchHint(val map: Map<Color, Color>) : PainterSvgPatchHint {
    override fun PainterProviderScope.patch(element: Element) {
        element.patchPalette(map)
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as ColorBasedReplacementPainterSvgPatchHint

        return map == other.map
    }

    override fun hashCode(): Int = map.hashCode()

    override fun toString(): String = "ColorBasedReplacementPainterSvgPatchHint(map=$map)"
}

internal fun Element.patchPalette(fill: Map<Color, Color>, stroke: Map<Color, Color> = fill) {
    patchColorAttribute("fill", fill)
    patchColorAttribute("stroke", stroke)

    val nodes = childNodes
    val length = nodes.length
    for (i in 0 until length) {
        val item = nodes.item(i)
        if (item is Element) {
            item.patchPalette(fill, stroke)
        }
    }
}

private fun Element.patchColorAttribute(attrName: String, pattern: Map<Color, Color>) {
    val color = getAttribute(attrName)
    val opacity = getAttribute("$attrName-opacity")

    if (color.isNotEmpty()) {
        val alpha = opacity.toFloatOrNull() ?: 1.0f
        val originalColor = tryParseColor(color, alpha) ?: return
        val newColor = pattern[originalColor] ?: return
        setAttribute(attrName, newColor.copy(alpha = 1.0f).toRgbaHexString(omitAlphaWhenFullyOpaque = true))
        if (newColor.alpha != alpha) {
            setAttribute("$attrName-opacity", newColor.alpha.toString())
        }
    }
}

private fun tryParseColor(color: String, alpha: Float): Color? {
    val rawColor = color.lowercase()
    if (rawColor.startsWith("#") && rawColor.length - 1 <= 8) {
        return fromHexOrNull(rawColor, alpha)
    }
    return null
}

private fun fromHexOrNull(rawColor: String, alpha: Float): Color? {
    val startPos =
        if (rawColor.startsWith("#")) {
            1
        } else if (rawColor.startsWith("0x")) {
            2
        } else {
            0
        }
    val length = rawColor.length - startPos
    val alphaOverride = alpha.takeIf { it != 1.0f }?.let { (it * 255).roundToInt() }

    return when (length) {
        3 ->
            Color(
                red = rawColor.substring(startPos, startPos + 1).toInt(16),
                green = rawColor.substring(startPos + 1, startPos + 2).toInt(16),
                blue = rawColor.substring(startPos + 2, startPos + 3).toInt(16),
                alpha = alphaOverride ?: 255,
            )

        4 ->
            Color(
                red = rawColor.substring(startPos, startPos + 1).toInt(16),
                green = rawColor.substring(startPos + 1, startPos + 2).toInt(16),
                blue = rawColor.substring(startPos + 2, startPos + 3).toInt(16),
                alpha = alphaOverride ?: rawColor.substring(startPos + 3, startPos + 4).toInt(16),
            )

        6 ->
            Color(
                red = rawColor.substring(startPos, startPos + 2).toInt(16),
                green = rawColor.substring(startPos + 2, startPos + 4).toInt(16),
                blue = rawColor.substring(startPos + 4, startPos + 6).toInt(16),
                alpha = alphaOverride ?: 255,
            )

        8 ->
            Color(
                red = rawColor.substring(startPos, startPos + 2).toInt(16),
                green = rawColor.substring(startPos + 2, startPos + 4).toInt(16),
                blue = rawColor.substring(startPos + 4, startPos + 6).toInt(16),
                alpha = alphaOverride ?: rawColor.substring(startPos + 6, startPos + 8).toInt(16),
            )

        else -> null
    }
}

/**
 * Creates a PainterHint that replaces all colors in the [paletteMap] with their corresponding new value. It is used in
 * IJ up to 23.3 to support patching the SVG colors for checkboxes and radio buttons.
 */
@Suppress("FunctionName")
public fun ColorBasedPaletteReplacement(paletteMap: Map<Color, Color>): PainterHint =
    if (paletteMap.isEmpty()) PainterHint.None else ColorBasedReplacementPainterSvgPatchHint(paletteMap)
