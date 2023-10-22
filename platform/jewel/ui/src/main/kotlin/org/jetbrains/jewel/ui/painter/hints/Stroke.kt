package org.jetbrains.jewel.ui.painter.hints

import androidx.compose.runtime.Immutable
import org.jetbrains.jewel.ui.painter.PainterHint
import org.jetbrains.jewel.ui.painter.PainterSuffixHint

@Immutable
private object StrokeImpl : PainterSuffixHint() {

    override fun suffix(): String = "_stroke"

    override fun toString(): String = "Stroke"
}

fun Stroke(stroked: Boolean = true): PainterHint = if (stroked) {
    StrokeImpl
} else {
    PainterHint.None
}
