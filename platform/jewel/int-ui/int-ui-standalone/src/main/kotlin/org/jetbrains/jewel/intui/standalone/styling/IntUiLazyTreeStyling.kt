package org.jetbrains.jewel.intui.standalone.styling

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.jetbrains.jewel.intui.core.theme.IntUiDarkTheme
import org.jetbrains.jewel.intui.core.theme.IntUiLightTheme
import org.jetbrains.jewel.intui.standalone.standalonePainterProvider
import org.jetbrains.jewel.painter.PainterProvider
import org.jetbrains.jewel.styling.LazyTreeColors
import org.jetbrains.jewel.styling.LazyTreeIcons
import org.jetbrains.jewel.styling.LazyTreeMetrics
import org.jetbrains.jewel.styling.LazyTreeStyle

@Stable
data class IntUiLazyTreeStyle(
    override val colors: IntUiLazyTreeColors,
    override val metrics: IntUiLazyTreeMetrics,
    override val icons: IntUiLazyTreeIcons,
) : LazyTreeStyle {

    companion object {

        @Composable
        fun light(
            colors: IntUiLazyTreeColors = IntUiLazyTreeColors.light(),
            metrics: IntUiLazyTreeMetrics = IntUiLazyTreeMetrics(),
            icons: IntUiLazyTreeIcons = intUiLazyTreeIcons(),
        ) = IntUiLazyTreeStyle(colors, metrics, icons)

        @Composable
        fun dark(
            colors: IntUiLazyTreeColors = IntUiLazyTreeColors.dark(),
            metrics: IntUiLazyTreeMetrics = IntUiLazyTreeMetrics(),
            icons: IntUiLazyTreeIcons = intUiLazyTreeIcons(),
        ) = IntUiLazyTreeStyle(colors, metrics, icons)
    }
}

@Immutable
data class IntUiLazyTreeColors(
    override val content: Color,
    override val contentFocused: Color,
    override val contentSelected: Color,
    override val contentSelectedFocused: Color,
    override val elementBackgroundFocused: Color,
    override val elementBackgroundSelected: Color,
    override val elementBackgroundSelectedFocused: Color,
) : LazyTreeColors {

    companion object {

        @Composable
        fun light(
            content: Color = Color.Unspecified,
            contentFocused: Color = content,
            contentSelected: Color = content,
            contentSelectedFocused: Color = content,
            nodeBackgroundFocused: Color = Color.Unspecified,
            nodeBackgroundSelected: Color = IntUiLightTheme.colors.grey(11),
            nodeBackgroundSelectedFocused: Color = IntUiLightTheme.colors.blue(11),
        ) = IntUiLazyTreeColors(
            content,
            contentFocused,
            contentSelected,
            contentSelectedFocused,
            nodeBackgroundFocused,
            nodeBackgroundSelected,
            nodeBackgroundSelectedFocused,
        )

        @Composable
        fun dark(
            content: Color = Color.Unspecified,
            contentFocused: Color = content,
            contentSelected: Color = content,
            contentSelectedFocused: Color = content,
            nodeBackgroundFocused: Color = Color.Unspecified,
            nodeBackgroundSelected: Color = IntUiDarkTheme.colors.grey(4),
            nodeBackgroundSelectedFocused: Color = IntUiDarkTheme.colors.blue(2),
        ) = IntUiLazyTreeColors(
            content,
            contentFocused,
            contentSelected,
            contentSelectedFocused,
            nodeBackgroundFocused,
            nodeBackgroundSelected,
            nodeBackgroundSelectedFocused,
        )
    }
}

@Stable
data class IntUiLazyTreeMetrics(
    override val indentSize: Dp = 7.dp + 16.dp,
    override val elementBackgroundCornerSize: CornerSize = CornerSize(2.dp),
    override val elementPadding: PaddingValues = PaddingValues(horizontal = 12.dp),
    override val elementContentPadding: PaddingValues = PaddingValues(4.dp),
    override val elementMinHeight: Dp = 24.dp,
    override val chevronContentGap: Dp = 2.dp,
) : LazyTreeMetrics

@Immutable
data class IntUiLazyTreeIcons(
    override val chevronCollapsed: PainterProvider,
    override val chevronExpanded: PainterProvider,
    override val chevronSelectedCollapsed: PainterProvider,
    override val chevronSelectedExpanded: PainterProvider,
) : LazyTreeIcons {

    companion object {

        @Composable
        fun chevronCollapsed(
            basePath: String = "expui/general/chevronRight.svg",
        ): PainterProvider = standalonePainterProvider(basePath)

        @Composable
        fun chevronExpanded(
            basePath: String = "expui/general/chevronDown.svg",
        ): PainterProvider = standalonePainterProvider(basePath)
    }
}

@Composable
fun intUiLazyTreeIcons(
    chevronCollapsed: PainterProvider =
        IntUiLazyTreeIcons.chevronCollapsed(),
    chevronExpanded: PainterProvider =
        IntUiLazyTreeIcons.chevronExpanded(),
    chevronSelectedCollapsed: PainterProvider =
        IntUiLazyTreeIcons.chevronCollapsed(),
    chevronSelectedExpanded: PainterProvider =
        IntUiLazyTreeIcons.chevronExpanded(),
) = IntUiLazyTreeIcons(chevronCollapsed, chevronExpanded, chevronSelectedCollapsed, chevronSelectedExpanded)
