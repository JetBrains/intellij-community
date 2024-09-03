package org.jetbrains.jewel.ui.painter.hints

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.isSpecified
import org.jetbrains.jewel.foundation.GenerateDataFunctions
import org.jetbrains.jewel.ui.painter.PainterHint
import org.jetbrains.jewel.ui.painter.PainterProviderScope
import org.jetbrains.jewel.ui.painter.PainterSuffixHint
import org.jetbrains.jewel.ui.painter.PainterSvgPatchHint
import org.w3c.dom.Element

@Immutable
private object DarkImpl : PainterSuffixHint() {
    override fun PainterProviderScope.suffix(): String = "_dark"

    override fun PainterProviderScope.canApply(): Boolean = acceptedHints.all { it !is StrokeImpl }

    override fun toString(): String = "Dark"
}

@Immutable
@GenerateDataFunctions
private class StrokeImpl(private val color: Color) : PainterSuffixHint(), PainterSvgPatchHint {
    override fun PainterProviderScope.suffix(): String = "_stroke"

    override fun PainterProviderScope.patch(element: Element) {
        if (path.contains(suffix())) return
        val palette = backgroundPalette.associateWith { Color.Transparent } + strokeColors.associateWith { color }
        element.patchPalette(palette)
    }

    override fun PainterProviderScope.canApply(): Boolean = true

    private val backgroundPalette =
        listOf(
            Color(0xFFEBECF0),
            Color(0xFFE7EFFD),
            Color(0xFFDFF2E0),
            Color(0xFFF2FCF3),
            Color(0xFFFFE8E8),
            Color(0xFFFFF5F5),
            Color(0xFFFFF8E3),
            Color(0xFFFFF4EB),
            Color(0xFFEEE0FF),
        )

    private val strokeColors =
        listOf(
            Color(0xFF000000),
            Color(0xFFFFFFFF),
            Color(0xFF818594),
            Color(0xFF6C707E),
            Color(0xFF3574F0),
            Color(0xFF5FB865),
            Color(0xFFE35252),
            Color(0xFFEB7171),
            Color(0xFFE3AE4D),
            Color(0xFFFCC75B),
            Color(0xFFF28C35),
            Color(0xFF955AE0),
        )

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is StrokeImpl) return false

        if (color != other.color) return false

        return true
    }

    override fun toString(): String = "Stroke(color=$color)"

    override fun hashCode(): Int = color.hashCode()
}

/** Transforms an SVG image to only draw its borders in the provided [color]. All fills are removed. */
@Suppress("FunctionName")
public fun Stroke(color: Color): PainterHint = if (color.isSpecified) StrokeImpl(color) else PainterHint.None

/**
 * Switches between the light and dark variants of an image based on [isDark]. If no dark image exists, the light image
 * will be used.
 *
 * All images that aren't dark images are base, or light, images.
 *
 * Dark images must be named in exactly the same way as the corresponding light image, but add a `_dark` suffix right
 * before the extension. Dark images must be placed in the same directory and have the same extension as their light
 * counterparts.
 *
 * Examples:
 *
 * | Light image name    | Dark image name          |
 * |---------------------|--------------------------|
 * | `my-icon.png`       | `my-icon_dark.png`       |
 * | `my-icon@20x20.svg` | `my-icon@20x20_dark.svg` |
 * | `my-icon@2x.png`    | `my-icon@2x_dark.png`    |
 */
@Suppress("FunctionName")
public fun Dark(isDark: Boolean = true): PainterHint = if (isDark) DarkImpl else PainterHint.None
