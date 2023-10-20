package org.jetbrains.jewel.intui.standalone.styling

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.jetbrains.jewel.GenerateDataFunctions
import org.jetbrains.jewel.intui.core.theme.IntUiDarkTheme
import org.jetbrains.jewel.intui.core.theme.IntUiLightTheme
import org.jetbrains.jewel.styling.GroupHeaderColors
import org.jetbrains.jewel.styling.GroupHeaderMetrics
import org.jetbrains.jewel.styling.GroupHeaderStyle

@Immutable
@GenerateDataFunctions
class IntUiGroupHeaderStyle(
    override val colors: IntUiGroupHeaderColors,
    override val metrics: IntUiGroupHeaderMetrics,
) : GroupHeaderStyle {

    companion object {

        @Composable
        fun light(
            colors: IntUiGroupHeaderColors = IntUiGroupHeaderColors.light(),
            metrics: IntUiGroupHeaderMetrics = IntUiGroupHeaderMetrics(),
        ) = IntUiGroupHeaderStyle(colors, metrics)

        @Composable
        fun dark(
            colors: IntUiGroupHeaderColors = IntUiGroupHeaderColors.dark(),
            metrics: IntUiGroupHeaderMetrics = IntUiGroupHeaderMetrics(),
        ) = IntUiGroupHeaderStyle(colors, metrics)
    }
}

@Immutable
@GenerateDataFunctions
class IntUiGroupHeaderColors(
    override val divider: Color,
) : GroupHeaderColors {

    companion object {

        @Composable
        fun light(
            divider: Color = IntUiLightTheme.colors.grey(12),
        ) = IntUiGroupHeaderColors(divider)

        @Composable
        fun dark(
            divider: Color = IntUiDarkTheme.colors.grey(3),
        ) = IntUiGroupHeaderColors(divider)
    }
}

@Immutable
@GenerateDataFunctions
class IntUiGroupHeaderMetrics(
    override val dividerThickness: Dp = 1.dp,
    override val indent: Dp = 8.dp,
) : GroupHeaderMetrics
