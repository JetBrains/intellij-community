package org.jetbrains.jewel.bridge.theme

import androidx.compose.ui.unit.dp
import com.intellij.ide.ui.laf.darcula.DarculaUIUtil
import com.intellij.ui.scale.JBUIScale
import com.intellij.util.ui.JBUI
import org.jetbrains.jewel.foundation.GlobalMetrics

public fun GlobalMetrics.Companion.readFromLaF(): GlobalMetrics =
    GlobalMetrics(
        outlineWidth = DarculaUIUtil.BW.unscaled.dp,
        // The rowHeight() function returns a scaled value, but we need the base value
        rowHeight = (JBUI.CurrentTheme.List.rowHeight() / JBUIScale.scale(1f)).dp,
    )
