package org.jetbrains.jewel.painter.hints

import androidx.compose.runtime.Immutable
import org.jetbrains.jewel.painter.BitmapPainterHint
import org.jetbrains.jewel.painter.PainterHint
import org.jetbrains.jewel.painter.PainterSuffixHint

@Immutable
private object HiDpiImpl : PainterSuffixHint(), BitmapPainterHint {

    override fun suffix(): String = "@2x"

    override fun toString(): String = "HiDpi"
}

fun HiDpi(isHiDpi: Boolean): PainterHint = if (isHiDpi) {
    HiDpiImpl
} else {
    PainterHint.None
}

fun HiDpi(density: androidx.compose.ui.unit.Density): PainterHint = HiDpi(density.density > 1)
