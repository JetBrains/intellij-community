package org.jetbrains.jewel.ui.painter.hints

import androidx.compose.runtime.Immutable
import org.jetbrains.jewel.ui.painter.PainterHint
import org.jetbrains.jewel.ui.painter.PainterProviderScope
import org.jetbrains.jewel.ui.painter.PainterSuffixHint

@Immutable
private object HiDpiImpl : PainterSuffixHint() {

    override fun PainterProviderScope.suffix(): String = "@2x"

    override fun PainterProviderScope.canApply(): Boolean =
        density > 1f && when (path.substringAfterLast('.').lowercase()) {
            "svg", "xml" -> false
            else -> true
        }

    override fun toString(): String = "HiDpi"
}

public fun HiDpi(): PainterHint = HiDpiImpl
