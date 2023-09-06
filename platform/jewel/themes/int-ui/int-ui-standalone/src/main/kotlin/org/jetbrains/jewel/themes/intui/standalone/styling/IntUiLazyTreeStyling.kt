package org.jetbrains.jewel.themes.intui.standalone.styling

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.jetbrains.jewel.SvgLoader
import org.jetbrains.jewel.foundation.tree.TreeElementState
import org.jetbrains.jewel.styling.LazyTreeColors
import org.jetbrains.jewel.styling.LazyTreeIcons
import org.jetbrains.jewel.styling.LazyTreeMetrics
import org.jetbrains.jewel.styling.LazyTreeStyle
import org.jetbrains.jewel.styling.PainterProvider
import org.jetbrains.jewel.styling.ResourcePainterProvider
import org.jetbrains.jewel.themes.intui.core.theme.IntUiDarkTheme
import org.jetbrains.jewel.themes.intui.core.theme.IntUiLightTheme

@Stable
data class IntUiLazyTreeStyle(
    override val colors: IntUiLazyTreeColors,
    override val metrics: IntUiLazyTreeMetrics,
    override val icons: IntUiLazyTreeIcons,
) : LazyTreeStyle {

    companion object {

        @Composable
        fun light(
            svgLoader: SvgLoader,
            colors: IntUiLazyTreeColors = IntUiLazyTreeColors.light(),
            metrics: IntUiLazyTreeMetrics = IntUiLazyTreeMetrics(),
            icons: IntUiLazyTreeIcons = intUiLazyTreeIcons(svgLoader),
        ) = IntUiLazyTreeStyle(colors, metrics, icons)

        @Composable
        fun dark(
            svgLoader: SvgLoader,
            colors: IntUiLazyTreeColors = IntUiLazyTreeColors.dark(),
            metrics: IntUiLazyTreeMetrics = IntUiLazyTreeMetrics(),
            icons: IntUiLazyTreeIcons = intUiLazyTreeIcons(svgLoader),
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
            content: Color = IntUiLightTheme.colors.grey(1),
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
            content: Color = IntUiDarkTheme.colors.grey(12),
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
    override val elementBackgroundCornerSize: CornerSize = CornerSize(4.dp),
    override val elementPadding: PaddingValues = PaddingValues(horizontal = 12.dp),
    override val elementContentPadding: PaddingValues = PaddingValues(4.dp),
    override val elementMinHeight: Dp = 24.dp,
    override val chevronContentGap: Dp = 2.dp,
) : LazyTreeMetrics

@Immutable
data class IntUiLazyTreeIcons(
    override val nodeChevronCollapsed: PainterProvider<TreeElementState>,
    override val nodeChevronExpanded: PainterProvider<TreeElementState>,
) : LazyTreeIcons {

    companion object {

        @Composable
        fun nodeChevronCollapsed(
            svgLoader: SvgLoader,
            basePath: String = "icons/intui/chevronRight.svg",
        ): PainterProvider<TreeElementState> =
            ResourcePainterProvider.stateful(basePath, svgLoader)

        @Composable
        fun nodeChevronExpanded(
            svgLoader: SvgLoader,
            basePath: String = "icons/intui/chevronDown.svg",
        ): PainterProvider<TreeElementState> =
            ResourcePainterProvider.stateful(basePath, svgLoader)
    }
}

@Composable
fun intUiLazyTreeIcons(
    svgLoader: SvgLoader,
    nodeChevronCollapsed: PainterProvider<TreeElementState> =
        IntUiLazyTreeIcons.nodeChevronCollapsed(svgLoader),
    nodeChevronExpanded: PainterProvider<TreeElementState> =
        IntUiLazyTreeIcons.nodeChevronExpanded(svgLoader),
) = IntUiLazyTreeIcons(nodeChevronCollapsed, nodeChevronExpanded)
