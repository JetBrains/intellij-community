package org.jetbrains.jewel.ui.painter.hints

import org.jetbrains.jewel.ui.painter.PainterHint
import org.jetbrains.jewel.ui.painter.PainterPathHint
import org.jetbrains.jewel.ui.painter.PainterProviderScope

private class OverrideImpl(private val iconOverride: Map<String, String>) : PainterPathHint {

    override fun PainterProviderScope.patch(): String = iconOverride[path] ?: path

    override fun toString(): String = "Override(${iconOverride.hashCode()})"

    override fun hashCode(): Int = iconOverride.hashCode()

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is OverrideImpl) return false

        if (iconOverride != other.iconOverride) return false

        return true
    }
}

fun Override(override: Map<String, String>): PainterHint = if (override.isEmpty()) {
    PainterHint.None
} else {
    OverrideImpl(override)
}
