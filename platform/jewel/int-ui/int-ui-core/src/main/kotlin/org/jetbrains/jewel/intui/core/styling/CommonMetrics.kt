package org.jetbrains.jewel.intui.core.styling

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import org.jetbrains.jewel.styling.DividerMetrics
import org.jetbrains.jewel.styling.TooltipMetrics
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

fun TooltipMetrics.Companion.defaults(
    contentPadding: PaddingValues = PaddingValues(vertical = 9.dp, horizontal = 12.dp),
    showDelay: Duration = 0.milliseconds,
    cornerSize: CornerSize = CornerSize(5.dp),
    borderWidth: Dp = 1.dp,
    shadowSize: Dp = 12.dp,
    tooltipOffset: DpOffset = DpOffset(0.dp, 20.dp),
    tooltipAlignment: Alignment.Horizontal = Alignment.Start,
) = TooltipMetrics(contentPadding, showDelay, cornerSize, borderWidth, shadowSize, tooltipOffset, tooltipAlignment)

fun DividerMetrics.Companion.defaults(
    thickness: Dp = 1.dp,
    startIndent: Dp = 0.dp,
) = DividerMetrics(thickness, startIndent)
