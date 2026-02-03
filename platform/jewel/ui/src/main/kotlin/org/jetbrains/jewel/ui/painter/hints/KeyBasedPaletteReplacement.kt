package org.jetbrains.jewel.ui.painter.hints

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import org.jetbrains.jewel.foundation.GenerateDataFunctions
import org.jetbrains.jewel.ui.painter.PainterHint
import org.jetbrains.jewel.ui.painter.PainterProviderScope
import org.jetbrains.jewel.ui.painter.PainterSvgPatchHint
import org.jetbrains.jewel.ui.util.toRgbaHexString
import org.w3c.dom.Element

@Immutable
@GenerateDataFunctions
private class KeyBasedReplacementPainterSvgPatchHint(val map: Map<String, Color>) : PainterSvgPatchHint {
    override fun PainterProviderScope.patch(element: Element) {
        element.patchPalette(map)
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as KeyBasedReplacementPainterSvgPatchHint

        return map == other.map
    }

    override fun hashCode(): Int = map.hashCode()

    override fun toString(): String = "KeyBasedReplacementPainterSvgPatchHint(map=$map)"
}

private fun Element.patchPalette(replacementColors: Map<String, Color>) {
    val id = getAttribute("id").ifEmpty { null }
    if (id != null) {
        val (fillKey, strokeKey) = parseKeysFromId(id)
        patchColorAttribute("fill", replacementColors[fillKey])
        patchColorAttribute("stroke", replacementColors[strokeKey])
    }

    val nodes = childNodes
    val length = nodes.length
    for (i in 0 until length) {
        val item = nodes.item(i)
        if (item is Element) {
            item.patchPalette(replacementColors)
        }
    }
}

private fun parseKeysFromId(id: String): Pair<String, String> {
    val parts = id.split('_')

    return if (parts.size == 2) {
        parts.first() to parts.last()
    } else {
        id to id
    }
}

private fun Element.patchColorAttribute(attrName: String, newColor: Color?) {
    if (newColor == null) return
    if (!hasAttribute(attrName)) return

    setAttribute(attrName, newColor.copy(alpha = 1.0f).toRgbaHexString(omitAlphaWhenFullyOpaque = true))
    if (newColor.alpha != 1f) {
        setAttribute("$attrName-opacity", newColor.alpha.toString())
    } else {
        removeAttribute("$attrName-opacity")
    }
}

/**
 * Creates a PainterHint that replaces colors with their corresponding new value, based on the IDs of each element. It
 * is used in IJ 24.1 and later to support patching the SVG colors for checkboxes and radio buttons.
 */
@Suppress("FunctionName")
public fun KeyBasedPaletteReplacement(paletteMap: Map<String, Color>): PainterHint =
    if (paletteMap.isEmpty()) PainterHint.None else KeyBasedReplacementPainterSvgPatchHint(paletteMap)
