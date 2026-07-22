// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.icons.impl.patchers

import com.intellij.platform.icons.design.Color
import com.intellij.platform.icons.impl.design.DefaultSRGB
import com.intellij.platform.icons.patchers.SvgPatcher
import com.intellij.platform.icons.patchers.svgPatcher
import org.jetbrains.annotations.ApiStatus

/**
 * The SVG patch that renders an icon as a monochrome outline in [stroke].
 *
 * Stroking is a *palette* substitution, not a blanket one: IntelliJ icons are authored from a fixed set of design
 * colors, so the background tints become transparent and the foreground tints become [stroke], while anything painted
 * in a color outside those two sets is deliberately left alone. Blanking every `fill` instead would erase icons drawn
 * entirely from filled shapes.
 *
 * Both the `fill` and the `stroke` attribute carry the substitution, because New UI icons come in two authoring
 * styles — `<path fill="#6C707E">` and `<path fill="none" stroke="#6C707E">` — and patching only one of them leaves
 * every icon of the other style in its authored gray.
 *
 * Every rendering backend has to stroke the same way, or one icon renders differently in a Swing toolbar than in a
 * Compose one; this is the single definition they are all expected to use.
 */
@ApiStatus.Internal
fun strokeSvgPatcher(stroke: Color): SvgPatcher = svgPatcher {
    for (color in strokeBackgroundPalette) {
        replaceIfMatches("fill", color.toHex(), "transparent")
        replaceIfMatches("stroke", color.toHex(), "transparent")
    }
    for (color in strokeForegroundPalette) {
        replaceIfMatches("fill", color.toHex(), stroke.toHex())
        replaceIfMatches("stroke", color.toHex(), stroke.toHex())
    }
    for (keyword in strokeForegroundKeywords) {
        replaceIfMatches("fill", keyword, stroke.toHex())
        replaceIfMatches("stroke", keyword, stroke.toHex())
    }
}

/**
 * The SVG patch for an icon that ships a hand-authored stroke variant, rendering it in [stroke].
 *
 * Such a variant is a separate drawing of the same glyph, already reduced to an outline by hand and authored in white:
 * it needs recoloring, not the palette reduction [strokeSvgPatcher] performs. Reducing it a second time could only take
 * away artwork the author put there deliberately, since a tint that reads as a background in a filled icon can be the
 * outline itself in a drawing made of outlines.
 *
 * Rendering one in opaque white patches nothing at all: the drawing is already that color, so it is left exactly as
 * authored, down to any opacity it carries. Recoloring it white instead would be a substitution of a color by itself
 * that still normalizes that opacity away.
 */
@ApiStatus.Internal
fun authoredStrokeSvgPatcher(stroke: Color): SvgPatcher = svgPatcher {
    val target = stroke.toHex()
    if (target.equals(OPAQUE_WHITE, ignoreCase = true)) return@svgPatcher

    for (color in authoredStrokePalette) {
        replaceIfMatches("fill", color, target)
        replaceIfMatches("stroke", color, target)
    }
}

/**
 * The file-name suffix marking a hand-authored stroke variant, as in `run.svg` and its `run_stroke.svg`.
 *
 * Every frontend resolves the variant by this same suffix, so that stroking an icon reaches the same file whichever one
 * renders it.
 */
@ApiStatus.Internal
const val AUTHORED_STROKE_VARIANT_SUFFIX: String = "_stroke"

/** The only color a hand-authored stroke variant is drawn in, in both spellings an SVG may use for it. */
private val authoredStrokePalette: List<String> = listOf("white", "#ffffff")

/** The hex [Color.toHex] produces for fully opaque white; a translucent white carries an alpha component instead. */
private const val OPAQUE_WHITE: String = "#ffffff"

/**
 * Foreground palette entries an SVG may spell as a color keyword rather than as hex.
 *
 * `fill="black"` and `fill="#000000"` are the same color to a renderer but not to an attribute-value comparison, so a
 * palette expressed only in hex silently misses the keyword spelling.
 */
private val strokeForegroundKeywords: List<String> = listOf("black", "white")

/** The design palette's background tints: the fills a stroked icon drops. */
private val strokeBackgroundPalette: List<Color> =
    listOf(
        DefaultSRGB.fromHex("#EBECF0"),
        DefaultSRGB.fromHex("#E7EFFD"),
        DefaultSRGB.fromHex("#DFF2E0"),
        DefaultSRGB.fromHex("#F2FCF3"),
        DefaultSRGB.fromHex("#FFE8E8"),
        DefaultSRGB.fromHex("#FFF5F5"),
        DefaultSRGB.fromHex("#FFF8E3"),
        DefaultSRGB.fromHex("#FFF4EB"),
        DefaultSRGB.fromHex("#EEE0FF"),
    )

/** The design palette's foreground tints: the fills a stroked icon recolors. */
private val strokeForegroundPalette: List<Color> =
    listOf(
        DefaultSRGB.fromHex("#000000"),
        DefaultSRGB.fromHex("#FFFFFF"),
        DefaultSRGB.fromHex("#818594"),
        DefaultSRGB.fromHex("#6C707E"),
        DefaultSRGB.fromHex("#3574F0"),
        DefaultSRGB.fromHex("#5FB865"),
        DefaultSRGB.fromHex("#E35252"),
        DefaultSRGB.fromHex("#EB7171"),
        DefaultSRGB.fromHex("#E3AE4D"),
        DefaultSRGB.fromHex("#FCC75B"),
        DefaultSRGB.fromHex("#F28C35"),
        DefaultSRGB.fromHex("#955AE0"),
        DefaultSRGB.fromHex("#A8ADBD"),
        DefaultSRGB.fromHex("#CED0D6"),
    )
