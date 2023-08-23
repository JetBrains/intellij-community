package org.jetbrains.jewel.themes.intui.standalone

import androidx.compose.foundation.shape.CornerSize
import androidx.compose.runtime.Immutable
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.jetbrains.jewel.GlobalMetrics

@Immutable
data class IntUiGlobalMetrics(
    override val outlineWidth: Dp = 2.dp,
    override val outlineCornerSize: CornerSize = CornerSize(3.dp),
) : GlobalMetrics
