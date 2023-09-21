package org.jetbrains.jewel.themes.intui.standalone

import androidx.compose.runtime.Immutable
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.jetbrains.jewel.GlobalMetrics

@Immutable
class IntUiGlobalMetrics(
    override val outlineWidth: Dp = 2.dp,
) : GlobalMetrics {

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as IntUiGlobalMetrics

        return outlineWidth == other.outlineWidth
    }

    override fun hashCode(): Int = outlineWidth.hashCode()

    override fun toString(): String =
        "IntUiGlobalMetrics(outlineWidth=$outlineWidth)"
}
