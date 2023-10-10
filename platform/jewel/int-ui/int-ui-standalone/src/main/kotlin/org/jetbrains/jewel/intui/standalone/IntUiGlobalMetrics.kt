package org.jetbrains.jewel.intui.standalone

import androidx.compose.runtime.Immutable
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.jetbrains.jewel.GlobalMetrics

@Immutable
class IntUiGlobalMetrics(
    override val outlineWidth: Dp = 2.dp,
    override val rowHeight: Dp = 24.dp,
) : GlobalMetrics {

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as IntUiGlobalMetrics

        if (outlineWidth != other.outlineWidth) return false
        if (rowHeight != other.rowHeight) return false

        return true
    }

    override fun hashCode(): Int {
        var result = outlineWidth.hashCode()
        result = 31 * result + rowHeight.hashCode()
        return result
    }

    override fun toString() =
        "IntUiGlobalMetrics(outlineWidth=$outlineWidth, rowHeight=$rowHeight)"
}
