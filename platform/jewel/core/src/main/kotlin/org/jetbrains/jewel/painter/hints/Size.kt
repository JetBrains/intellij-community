package org.jetbrains.jewel.painter.hints

import androidx.compose.runtime.Immutable
import org.jetbrains.jewel.painter.PainterHint
import org.jetbrains.jewel.painter.PainterSuffixHint
import org.jetbrains.jewel.painter.SvgPainterHint

@Immutable
private class SizeImpl(private val size: String) : PainterSuffixHint(), SvgPainterHint {

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

private val sizeHintValidateRegex = """\d+x\d+""".toRegex()

fun Size(size: String): PainterHint {
    if (size.isBlank()) return PainterHint.None
    val trimmed = size.trim()
    require(sizeHintValidateRegex.matches(trimmed)) {
        "Size must be in format of <width>x<height>"
    }

    return SizeImpl(trimmed)
}

fun Size(width: Int, height: Int = width): PainterHint {
    require(width > 0 && height > 0) {
        "Width and height must be positive"
    }

    return SizeImpl("${width}x$height")
}
