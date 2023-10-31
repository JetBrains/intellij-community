package org.jetbrains.jewel.ui.painter.hints

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.unit.dp
import org.jetbrains.jewel.ui.painter.PainterHint
import org.jetbrains.jewel.ui.painter.PainterProviderScope
import org.jetbrains.jewel.ui.painter.PainterSuffixHint
import org.jetbrains.jewel.ui.painter.PainterWrapperHint
import org.jetbrains.jewel.ui.painter.ResizedPainter
import org.jetbrains.jewel.ui.painter.SvgPainterHint

@Immutable
private class SizeImpl(
    private val width: Int,
    private val height: Int,
) : PainterSuffixHint(), PainterWrapperHint, SvgPainterHint {

    override fun PainterProviderScope.suffix(): String = buildString {
        append("@")
        append(width)
        append("x")
        append(height)
    }

    override fun PainterProviderScope.wrap(painter: Painter): Painter {
        if (path.contains(suffix())) return painter

        return ResizedPainter(
            painter,
            androidx.compose.ui.geometry.Size(width.dp.toPx(), height.dp.toPx()),
        )
    }

    override fun toString(): String = "Size(width=$width, height=$height)"

    override fun hashCode(): Int {
        var result = width
        result = 31 * result + height
        return result
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is SizeImpl) return false

        if (width != other.width) return false
        if (height != other.height) return false

        return true
    }
}

fun Size(width: Int, height: Int = width): PainterHint {
    require(width > 0 && height > 0) {
        "Width and height must be positive"
    }

    return SizeImpl(width, height)
}
