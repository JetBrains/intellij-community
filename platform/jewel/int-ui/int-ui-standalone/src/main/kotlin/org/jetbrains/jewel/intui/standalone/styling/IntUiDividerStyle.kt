package org.jetbrains.jewel.intui.standalone.styling

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.jetbrains.jewel.intui.core.theme.IntUiDarkTheme
import org.jetbrains.jewel.intui.core.theme.IntUiLightTheme
import org.jetbrains.jewel.styling.DividerMetrics
import org.jetbrains.jewel.styling.DividerStyle

@Immutable
class IntUiDividerStyle(
    override val color: Color,
    override val metrics: DividerMetrics,
) : DividerStyle {

    companion object {

        @Composable
        fun light(
            color: Color = IntUiLightTheme.colors.grey(12),
            metrics: IntUiDividerMetrics = IntUiDividerMetrics(),
        ) = IntUiDividerStyle(color, metrics)

        @Composable
        fun dark(
            color: Color = IntUiDarkTheme.colors.grey(1),
            metrics: IntUiDividerMetrics = IntUiDividerMetrics(),
        ) = IntUiDividerStyle(color, metrics)
    }
}

@Immutable
class IntUiDividerMetrics(
    override val thickness: Dp = 1.dp,
    override val startIndent: Dp = 0.dp,
) : DividerMetrics
