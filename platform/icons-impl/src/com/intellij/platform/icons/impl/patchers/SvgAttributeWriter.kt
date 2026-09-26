// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.icons.impl.patchers

import org.jetbrains.annotations.ApiStatus

/**
 * Writes [value] into [attributeName], keeping a color attribute and its paired opacity attribute consistent.
 *
 * A color attribute carries no alpha channel: alpha belongs on `fill-opacity` and its siblings. A color written into
 * one is therefore split across the pair, and the opacity the document was authored with gives way to the one being
 * written — it described the color that has just been replaced, not the new one.
 *
 * Writing lives here rather than in each renderer for the same reason condition evaluation lives on the operation: a
 * frontend that split the pair differently would draw the same icon differently.
 */
@ApiStatus.Internal
fun writeSvgAttribute(
    attributeName: String,
    value: String,
    setAttribute: (String, String) -> Unit,
    removeAttribute: (String) -> Unit,
) {
    val opacityAttributeName = OPACITY_ATTRIBUTES[attributeName]
    val color = value.toOpaqueHexAndAlpha()
    if (opacityAttributeName == null || color == null) {
        setAttribute(attributeName, value)
        return
    }

    val (rgb, alpha) = color
    setAttribute(attributeName, rgb)
    if (alpha == OPAQUE) {
        removeAttribute(opacityAttributeName)
    } else {
        setAttribute(opacityAttributeName, (alpha / OPAQUE.toFloat()).toString())
    }
}

/** The color attributes that have a paired opacity attribute, and the attribute that holds it. */
private val OPACITY_ATTRIBUTES =
    mapOf(
        "fill" to "fill-opacity",
        "stroke" to "stroke-opacity",
        "stop-color" to "stop-opacity",
        "flood-color" to "flood-opacity",
    )

private const val OPAQUE = 0xFF

/**
 * This hex literal split into the lowercase `#rrggbb` it names and its alpha, or `null` when it names no color.
 *
 * A color carries 3, 4, 6 or 8 significant digits, with the two shorthands standing for doubled pairs; an omitted
 * alpha is fully opaque.
 */
private fun String.toOpaqueHexAndAlpha(): Pair<String, Int>? {
    if (!startsWith('#')) return null

    val digits = drop(1).lowercase()
    if (!digits.all { it in '0'..'9' || it in 'a'..'f' }) return null

    val expanded =
        when (digits.length) {
            3, 4 -> digits.map { "$it$it" }.joinToString(separator = "")
            6, 8 -> digits
            else -> return null
        }
    val alpha = if (expanded.length == 8) expanded.substring(6).toInt(radix = 16) else OPAQUE
    return "#${expanded.substring(0, 6)}" to alpha
}
