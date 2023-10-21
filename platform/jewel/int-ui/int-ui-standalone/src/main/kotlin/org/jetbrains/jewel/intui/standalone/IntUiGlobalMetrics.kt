package org.jetbrains.jewel.intui.standalone

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.jetbrains.jewel.GlobalMetrics

fun GlobalMetrics.Companion.defaults(
    outlineWidth: Dp = 2.dp,
    rowHeight: Dp = 24.dp,
) = GlobalMetrics(outlineWidth, rowHeight)
