package org.jetbrains.jewel.painter.hints

import androidx.compose.runtime.Immutable
import org.jetbrains.jewel.painter.PainterHint
import org.jetbrains.jewel.painter.PainterSuffixHint

@Immutable
private class SizeImpl(private val size: String) : PainterSuffixHint() {

    override fun suffix(): String = buildString {
        append("@")
        append(size)
    }

    override fun toString(): String = "Size(size=$size)"

    override fun hashCode(): Int = size.hashCode()

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is SizeImpl) return false

        if (size != other.size) return false

        return true
    }
}

fun Size(size: String?): PainterHint = if (size.isNullOrEmpty()) {
    PainterHint.None
} else {
    SizeImpl(size)
}

fun Size(width: Int, height: Int): PainterHint = SizeImpl("${width}x$height")
