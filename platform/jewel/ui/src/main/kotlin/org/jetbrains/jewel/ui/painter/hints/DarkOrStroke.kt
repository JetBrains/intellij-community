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

    private val backgroundPalette = listOf(
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

    private val strokeColors = listOf(
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
}

public fun Stroke(color: Color): PainterHint =
    if (color.isSpecified) StrokeImpl(color) else PainterHint.None

public fun Dark(isDark: Boolean = true): PainterHint =
    if (isDark) DarkImpl else PainterHint.None
