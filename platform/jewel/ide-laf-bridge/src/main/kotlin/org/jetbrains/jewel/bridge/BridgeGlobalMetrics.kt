package org.jetbrains.jewel.bridge

import androidx.compose.runtime.Immutable
import androidx.compose.ui.unit.Dp
import com.intellij.ide.ui.laf.darcula.DarculaUIUtil
import org.jetbrains.jewel.GlobalMetrics

@Immutable
internal class BridgeGlobalMetrics(
    override val outlineWidth: Dp,
) : GlobalMetrics {

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as BridgeGlobalMetrics

        if (outlineWidth != other.outlineWidth) return false

        return true
    }

    override fun hashCode(): Int = outlineWidth.hashCode()

    override fun toString(): String =
        "BridgeGlobalMetrics(outlineWidth=$outlineWidth)"

    companion object {

        fun readFromLaF() = BridgeGlobalMetrics(
            outlineWidth = DarculaUIUtil.BW.dp,
        )
    }
}
