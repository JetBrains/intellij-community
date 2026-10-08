// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jewel.ui.painter.hints

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import org.jetbrains.jewel.foundation.GenerateDataFunctions
import org.jetbrains.jewel.ui.painter.PainterHint
import org.jetbrains.jewel.ui.painter.PainterProviderScope
import org.jetbrains.jewel.ui.painter.PainterSvgPatchHint
import org.w3c.dom.Element

private const val COLOR_FILL_KEY = "color-fill-key"
private const val COLOR_STROKE_KEY = "color-stroke-key"

@Immutable
@GenerateDataFunctions
private class ColorKeyReplacementPainterSvgPatchHint(val map: Map<String, Color>) : PainterSvgPatchHint {
    override fun PainterProviderScope.patch(element: Element) {
        element.patchPalette(map)
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as ColorKeyReplacementPainterSvgPatchHint

        return map == other.map
    }

    override fun hashCode(): Int = map.hashCode()

    override fun toString(): String = "ColorKeyReplacementPainterSvgPatchHint(map=$map)"
}

private fun Element.patchPalette(replacementColors: Map<String, Color>) {
    getAttribute(COLOR_FILL_KEY).ifEmpty { null }?.let { patchColorAttribute("fill", replacementColors[it]) }
    getAttribute(COLOR_STROKE_KEY).ifEmpty { null }?.let { patchColorAttribute("stroke", replacementColors[it]) }

    val nodes = childNodes
    val length = nodes.length
    for (i in 0 until length) {
        val item = nodes.item(i)
        if (item is Element) {
            item.patchPalette(replacementColors)
        }
    }
}

/**
 * Creates a [PainterHint] that recolors the elements of an SVG that name a theme color in a `color-fill-key` or a
 * `color-stroke-key` attribute. Each named color replaces the `fill` or the `stroke` of its element. An element keeps
 * its original color when the map has no entry for its key.
 *
 * The IntelliJ Platform toggle images (`toggle*.svg`) use these attributes to refer to the theme's `toggle-*` colors.
 *
 * @param paletteMap The replacement colors, keyed by the theme color name, for example `toggle-on-bg`.
 */
@Suppress("FunctionName")
public fun ColorKeyPaletteReplacement(paletteMap: Map<String, Color>): PainterHint =
    if (paletteMap.isEmpty()) PainterHint.None else ColorKeyReplacementPainterSvgPatchHint(paletteMap)
