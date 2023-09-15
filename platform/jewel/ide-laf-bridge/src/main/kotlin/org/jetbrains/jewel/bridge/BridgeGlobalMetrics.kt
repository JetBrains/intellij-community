package org.jetbrains.jewel.bridge

import androidx.compose.runtime.Immutable
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.intellij.ide.ui.laf.darcula.DarculaUIUtil
import com.intellij.util.ui.UIUtil
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

        fun readFromLaF(): BridgeGlobalMetrics {
            // Copied from DarculaUIUtil.doPaint(java.awt.Graphics2D, int, int, float, float, boolean)
            // except that scaling is all moved into the .dp operation below
            val f = if (UIUtil.isRetina()) 0.5f else 1.0f
            val lw = if (UIUtil.isUnderDefaultMacTheme()) f else DarculaUIUtil.LW.unscaled

            return BridgeGlobalMetrics(outlineWidth = (DarculaUIUtil.BW.unscaled + lw).dp)
        }
    }
}
