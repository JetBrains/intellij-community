package org.jetbrains.jewel

import androidx.compose.foundation.TooltipArea
import androidx.compose.foundation.TooltipPlacement
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import org.jetbrains.jewel.styling.TooltipStyle

@Composable fun Tooltip(
    tooltip: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    tooltipPlacement: TooltipPlacement = TooltipPlacement.ComponentRect(
        alignment = Alignment.CenterEnd,
        anchor = Alignment.BottomEnd,
        offset = DpOffset(4.dp, 4.dp),
    ),
    style: TooltipStyle = IntelliJTheme.tooltipStyle,
    content: @Composable () -> Unit,
) {
    TooltipArea(
        tooltip = {
            CompositionLocalProvider(
                LocalContentColor provides style.colors.content,
            ) {
                Box(
                    modifier = Modifier
                        .shadow(
                            elevation = style.metrics.shadowSize,
                            shape = RoundedCornerShape(style.metrics.cornerSize),
                            ambientColor = style.colors.shadow,
                            spotColor = Color.Transparent,
                        )
                        .background(
                            color = style.colors.background,
                            shape = RoundedCornerShape(style.metrics.cornerSize),
                        )
                        .border(
                            width = style.metrics.borderWidth,
                            color = style.colors.border,
                            shape = RoundedCornerShape(style.metrics.cornerSize),
                        )
                        .padding(style.metrics.contentPadding),
                ) {
                    tooltip()
                }
            }
        },
        modifier = modifier,
        delayMillis = style.metrics.showDelay.inWholeMilliseconds.toInt(),
        tooltipPlacement = tooltipPlacement,
        content = content,
    )
}
