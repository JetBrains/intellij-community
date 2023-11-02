package org.jetbrains.jewel.bridge.theme

import androidx.compose.ui.unit.dp
import com.intellij.ide.ui.laf.darcula.DarculaUIUtil
import com.intellij.ui.scale.JBUIScale
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import org.jetbrains.jewel.foundation.GlobalMetrics

public fun GlobalMetrics.Companion.readFromLaF(): GlobalMetrics {
    // Copied from DarculaUIUtil.doPaint(java.awt.Graphics2D, int, int, float, float, boolean)
    // except that scaling is all moved into the .dp operation below
    val f = if (UIUtil.isRetina()) 0.5f else 1.0f
    val lw = if (UIUtil.isUnderDefaultMacTheme()) f else DarculaUIUtil.LW.unscaled

    return GlobalMetrics(
        outlineWidth = (DarculaUIUtil.BW.unscaled + lw).dp,
        // The rowHeight() function returns a scaled value, but we need the base value
        rowHeight = (JBUI.CurrentTheme.List.rowHeight() / JBUIScale.scale(1f)).dp,
    )
}
