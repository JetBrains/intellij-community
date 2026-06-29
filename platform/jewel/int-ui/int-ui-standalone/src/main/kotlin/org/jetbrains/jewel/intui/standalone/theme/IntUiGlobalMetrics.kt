package org.jetbrains.jewel.intui.standalone.theme

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.jetbrains.jewel.foundation.GlobalMetrics

/** Creates an Int UI default [GlobalMetrics] with the provided parameters. */
public fun GlobalMetrics.Companion.defaults(outlineWidth: Dp = 2.dp, rowHeight: Dp = 24.dp): GlobalMetrics =
    GlobalMetrics(outlineWidth, rowHeight)
