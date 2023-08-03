package org.jetbrains.jewel.themes.intui.standalone.styling

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.jetbrains.jewel.ButtonState
import org.jetbrains.jewel.SvgLoader
import org.jetbrains.jewel.styling.ResourcePainterProvider
import org.jetbrains.jewel.styling.StatefulPainterProvider
import org.jetbrains.jewel.styling.TabColors
import org.jetbrains.jewel.styling.TabContentAlpha
import org.jetbrains.jewel.styling.TabIcons
import org.jetbrains.jewel.styling.TabMetrics
import org.jetbrains.jewel.styling.TabStyle
import org.jetbrains.jewel.themes.intui.core.theme.IntUiDarkTheme
import org.jetbrains.jewel.themes.intui.core.theme.IntUiLightTheme

data class IntUiTabStyle(
    override val colors: TabColors,
    override val metrics: TabMetrics,
    override val icons: TabIcons,
    override val contentAlpha: TabContentAlpha,
) : TabStyle {

    object Default {

        @Composable
        fun light(
            svgLoader: SvgLoader,
            colors: TabColors = IntUiTabColors.Default.light(),
            metrics: TabMetrics = IntUiTabMetrics(),
            icons: TabIcons = intUiTabIcons(svgLoader),
            contentAlpha: TabContentAlpha = IntUiTabContentAlpha.default(),
        ) = IntUiTabStyle(colors, metrics, icons, contentAlpha)

        @Composable
        fun dark(
            svgLoader: SvgLoader,
            colors: TabColors = IntUiTabColors.Default.dark(),
            metrics: TabMetrics = IntUiTabMetrics(),
            icons: TabIcons = intUiTabIcons(svgLoader),
            contentAlpha: TabContentAlpha = IntUiTabContentAlpha.default(),
        ) = IntUiTabStyle(colors, metrics, icons, contentAlpha)
    }

    object Editor {

        @Composable
        fun light(
            svgLoader: SvgLoader,
            colors: TabColors = IntUiTabColors.Editor.light(),
            metrics: TabMetrics = IntUiTabMetrics(),
            icons: TabIcons = intUiTabIcons(svgLoader),
            contentAlpha: TabContentAlpha = IntUiTabContentAlpha.editor(),
        ) = IntUiTabStyle(colors, metrics, icons, contentAlpha)

        @Composable
        fun dark(
            svgLoader: SvgLoader,
            colors: TabColors = IntUiTabColors.Editor.dark(),
            metrics: TabMetrics = IntUiTabMetrics(),
            icons: TabIcons = intUiTabIcons(svgLoader),
            contentAlpha: TabContentAlpha = IntUiTabContentAlpha.editor(),
        ) = IntUiTabStyle(colors, metrics, icons, contentAlpha)
    }
}

data class IntUiTabIcons(
    override val close: StatefulPainterProvider<ButtonState>,
) : TabIcons {

    companion object {

        @Composable
        fun close(
            svgLoader: SvgLoader,
            basePath: String = "icons/intui/closeSmall.svg",
        ): StatefulPainterProvider<ButtonState> =
            ResourcePainterProvider(basePath, svgLoader)
    }
}

@Composable
fun intUiTabIcons(
    svgLoader: SvgLoader,
    close: StatefulPainterProvider<ButtonState> = IntUiTabIcons.close(svgLoader),
) = IntUiTabIcons(close)

@Immutable
data class IntUiTabMetrics(
    override val underlineThickness: Dp = 3.dp,
    override val tabPadding: PaddingValues = PaddingValues(horizontal = 8.dp),
    override val closeContentGap: Dp = 8.dp,
    override val tabHeight: Dp = 40.dp,
) : TabMetrics

@Immutable
data class IntUiTabColors(
    override val background: Color,
    override val backgroundDisabled: Color,
    override val backgroundFocused: Color,
    override val backgroundPressed: Color,
    override val backgroundHovered: Color,
    override val backgroundSelected: Color,
    override val content: Color,
    override val contentDisabled: Color,
    override val contentFocused: Color,
    override val contentPressed: Color,
    override val contentHovered: Color,
    override val contentSelected: Color,
    override val underline: Color,
    override val underlineDisabled: Color,
    override val underlineFocused: Color,
    override val underlinePressed: Color,
    override val underlineHovered: Color,
    override val underlineSelected: Color,
) : TabColors {

    object Default {

        fun light(
            background: Color = IntUiLightTheme.colors.grey(14),
            backgroundFocused: Color = background,
            backgroundHovered: Color = IntUiLightTheme.colors.grey(12),
            backgroundPressed: Color = background,
            backgroundSelected: Color = background,
            backgroundDisabled: Color = background,
            content: Color = IntUiLightTheme.colors.grey(1),
            contentHovered: Color = content,
            contentDisabled: Color = content,
            contentPressed: Color = content,
            contentFocused: Color = content,
            contentSelected: Color = content,
            underline: Color = Color.Unspecified,
            underlineHovered: Color = underline,
            underlineDisabled: Color = underline,
            underlinePressed: Color = underline,
            underlineFocused: Color = underline,
            underlineSelected: Color = IntUiLightTheme.colors.blue(4),
        ) = IntUiTabColors(
            background,
            backgroundDisabled,
            backgroundFocused,
            backgroundPressed,
            backgroundHovered,
            backgroundSelected,
            content,
            contentDisabled,
            contentFocused,
            contentPressed,
            contentHovered,
            contentSelected,
            underline,
            underlineDisabled,
            underlineFocused,
            underlinePressed,
            underlineHovered,
            underlineSelected
        )

        fun dark(
            background: Color = Color.Unspecified,
            backgroundFocused: Color = background,
            backgroundHovered: Color = IntUiDarkTheme.colors.grey(4),
            backgroundPressed: Color = backgroundHovered,
            backgroundSelected: Color = background,
            backgroundDisabled: Color = background,
            content: Color = IntUiDarkTheme.colors.grey(12),
            contentHovered: Color = content,
            contentDisabled: Color = content,
            contentPressed: Color = content,
            contentFocused: Color = content,
            contentSelected: Color = content,
            underline: Color = Color.Unspecified,
            underlineHovered: Color = underline,
            underlineDisabled: Color = underline,
            underlinePressed: Color = underline,
            underlineFocused: Color = underline,
            underlineSelected: Color = IntUiDarkTheme.colors.blue(6),
        ) = IntUiTabColors(
            background,
            backgroundDisabled,
            backgroundFocused,
            backgroundPressed,
            backgroundHovered,
            backgroundSelected,
            content,
            contentDisabled,
            contentFocused,
            contentPressed,
            contentHovered,
            contentSelected,
            underline,
            underlineDisabled,
            underlineFocused,
            underlinePressed,
            underlineHovered,
            underlineSelected
        )
    }

    object Editor {

        fun light(
            background: Color = Color.Transparent,
            backgroundFocused: Color = background,
            backgroundHovered: Color = background,
            backgroundPressed: Color = background,
            backgroundSelected: Color = background,
            backgroundDisabled: Color = background,
            content: Color = IntUiLightTheme.colors.grey(3),
            contentHovered: Color = content,
            contentDisabled: Color = content,
            contentPressed: Color = content,
            contentFocused: Color = content,
            contentSelected: Color = content,
            underline: Color = Color.Unspecified,
            underlineHovered: Color = underline,
            underlineDisabled: Color = underline,
            underlinePressed: Color = underline,
            underlineFocused: Color = underline,
            underlineSelected: Color = IntUiLightTheme.colors.blue(4),
        ) = IntUiTabColors(
            background,
            backgroundDisabled,
            backgroundFocused,
            backgroundPressed,
            backgroundHovered,
            backgroundSelected,
            content,
            contentDisabled,
            contentFocused,
            contentPressed,
            contentHovered,
            contentSelected,
            underline,
            underlineDisabled,
            underlineFocused,
            underlinePressed,
            underlineHovered,
            underlineSelected
        )

        fun dark(
            background: Color = Color.Unspecified,
            backgroundFocused: Color = background,
            backgroundHovered: Color = background,
            backgroundPressed: Color = background,
            backgroundSelected: Color = background,
            backgroundDisabled: Color = background,

            content: Color = IntUiDarkTheme.colors.grey(12),
            contentHovered: Color = content,
            contentDisabled: Color = content,
            contentPressed: Color = content,
            contentFocused: Color = content,
            contentSelected: Color = content,

            underline: Color = Color.Unspecified,
            underlineHovered: Color = underline,
            underlineDisabled: Color = underline,
            underlinePressed: Color = underline,
            underlineFocused: Color = underline,
            underlineSelected: Color = IntUiDarkTheme.colors.blue(6),
        ) = IntUiTabColors(
            background,
            backgroundDisabled,
            backgroundFocused,
            backgroundPressed,
            backgroundHovered,
            backgroundSelected,
            content,
            contentDisabled,
            contentFocused,
            contentPressed,
            contentHovered,
            contentSelected,
            underline,
            underlineDisabled,
            underlineFocused,
            underlinePressed,
            underlineHovered,
            underlineSelected
        )
    }
}

@Immutable
data class IntUiTabContentAlpha(
    override val normal: Float,
    override val disabled: Float,
    override val focused: Float,
    override val pressed: Float,
    override val hovered: Float,
    override val selected: Float,
) : TabContentAlpha {

    companion object {

        fun default(
            normal: Float = 1f,
            disabled: Float = normal,
            focused: Float = normal,
            pressed: Float = normal,
            hovered: Float = normal,
            selected: Float = normal,
        ) =
            IntUiTabContentAlpha(normal, disabled, focused, pressed, hovered, selected)

        fun editor(
            normal: Float = .75f,
            disabled: Float = normal,
            focused: Float = normal,
            pressed: Float = 1f,
            hovered: Float = pressed,
            selected: Float = pressed,
        ) =
            IntUiTabContentAlpha(normal, disabled, focused, pressed, hovered, selected)
    }
}
