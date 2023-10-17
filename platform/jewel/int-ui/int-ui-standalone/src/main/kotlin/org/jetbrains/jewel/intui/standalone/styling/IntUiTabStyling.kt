package org.jetbrains.jewel.intui.standalone.styling

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.jetbrains.jewel.intui.core.theme.IntUiDarkTheme
import org.jetbrains.jewel.intui.core.theme.IntUiLightTheme
import org.jetbrains.jewel.intui.standalone.standalonePainterProvider
import org.jetbrains.jewel.painter.PainterProvider
import org.jetbrains.jewel.styling.TabColors
import org.jetbrains.jewel.styling.TabContentAlpha
import org.jetbrains.jewel.styling.TabIcons
import org.jetbrains.jewel.styling.TabMetrics
import org.jetbrains.jewel.styling.TabStyle

data class IntUiTabStyle(
    override val colors: TabColors,
    override val metrics: TabMetrics,
    override val icons: TabIcons,
    override val contentAlpha: TabContentAlpha,
) : TabStyle {

    object Default {

        @Composable
        fun light(
            colors: TabColors = IntUiTabColors.Default.light(),
            metrics: TabMetrics = IntUiTabMetrics(),
            icons: TabIcons = intUiTabIcons(),
            contentAlpha: TabContentAlpha = IntUiTabContentAlpha.default(),
        ) = IntUiTabStyle(colors, metrics, icons, contentAlpha)

        @Composable
        fun dark(
            colors: TabColors = IntUiTabColors.Default.dark(),
            metrics: TabMetrics = IntUiTabMetrics(),
            icons: TabIcons = intUiTabIcons(),
            contentAlpha: TabContentAlpha = IntUiTabContentAlpha.default(),
        ) = IntUiTabStyle(colors, metrics, icons, contentAlpha)
    }

    object Editor {

        @Composable
        fun light(
            colors: TabColors = IntUiTabColors.Editor.light(),
            metrics: TabMetrics = IntUiTabMetrics(),
            icons: TabIcons = intUiTabIcons(),
            contentAlpha: TabContentAlpha = IntUiTabContentAlpha.editor(),
        ) = IntUiTabStyle(colors, metrics, icons, contentAlpha)

        @Composable
        fun dark(
            colors: TabColors = IntUiTabColors.Editor.dark(),
            metrics: TabMetrics = IntUiTabMetrics(),
            icons: TabIcons = intUiTabIcons(),
            contentAlpha: TabContentAlpha = IntUiTabContentAlpha.editor(),
        ) = IntUiTabStyle(colors, metrics, icons, contentAlpha)
    }
}

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
            backgroundPressed: Color = backgroundHovered,
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
            underlineSelected,
        )

        fun dark(
            background: Color = Color.Unspecified,
            backgroundFocused: Color = background,
            backgroundHovered: Color = IntUiDarkTheme.colors.grey(4),
            backgroundPressed: Color = backgroundHovered,
            backgroundSelected: Color = background,
            backgroundDisabled: Color = background,
            content: Color = Color.Unspecified,
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
            underlineSelected,
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
            content: Color = Color.Unspecified,
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
            underlineSelected,
        )

        fun dark(
            background: Color = Color.Unspecified,
            backgroundFocused: Color = background,
            backgroundHovered: Color = background,
            backgroundPressed: Color = background,
            backgroundSelected: Color = background,
            backgroundDisabled: Color = background,

            content: Color = Color.Unspecified,
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
            underlineSelected,
        )
    }
}

@Immutable
data class IntUiTabMetrics(
    override val underlineThickness: Dp = 3.dp,
    override val tabPadding: PaddingValues = PaddingValues(horizontal = 8.dp),
    override val closeContentGap: Dp = 8.dp,
    override val tabHeight: Dp = 40.dp,
) : TabMetrics

@Immutable
data class IntUiTabContentAlpha(
    override val iconNormal: Float,
    override val iconDisabled: Float,
    override val iconFocused: Float,
    override val iconPressed: Float,
    override val iconHovered: Float,
    override val iconSelected: Float,
    override val labelNormal: Float,
    override val labelDisabled: Float,
    override val labelFocused: Float,
    override val labelPressed: Float,
    override val labelHovered: Float,
    override val labelSelected: Float,
) : TabContentAlpha {

    companion object {

        fun default(
            iconNormal: Float = 1f,
            iconDisabled: Float = iconNormal,
            iconFocused: Float = iconNormal,
            iconPressed: Float = iconNormal,
            iconHovered: Float = iconNormal,
            iconSelected: Float = iconNormal,
            labelNormal: Float = iconNormal,
            labelDisabled: Float = iconNormal,
            labelFocused: Float = iconNormal,
            labelPressed: Float = iconNormal,
            labelHovered: Float = iconNormal,
            labelSelected: Float = iconNormal,
        ) = IntUiTabContentAlpha(
            iconNormal,
            iconDisabled,
            iconFocused,
            iconPressed,
            iconHovered,
            iconSelected,
            labelNormal,
            labelDisabled,
            labelFocused,
            labelPressed,
            labelHovered,
            labelSelected,
        )

        fun editor(
            iconNormal: Float = .7f,
            iconDisabled: Float = iconNormal,
            iconFocused: Float = iconNormal,
            iconPressed: Float = 1f,
            iconHovered: Float = iconPressed,
            iconSelected: Float = iconPressed,
            labelNormal: Float = .9f,
            labelDisabled: Float = labelNormal,
            labelFocused: Float = labelNormal,
            labelPressed: Float = 1f,
            labelHovered: Float = labelPressed,
            labelSelected: Float = labelPressed,
        ) =
            IntUiTabContentAlpha(
                iconNormal,
                iconDisabled,
                iconFocused,
                iconPressed,
                iconHovered,
                iconSelected,
                labelNormal,
                labelDisabled,
                labelFocused,
                labelPressed,
                labelHovered,
                labelSelected,
            )
    }
}

data class IntUiTabIcons(
    override val close: PainterProvider,
) : TabIcons {

    companion object {

        @Composable
        fun close(
            basePath: String = "expui/general/closeSmall.svg",
        ): PainterProvider = standalonePainterProvider(basePath)
    }
}

@Composable
fun intUiTabIcons(
    close: PainterProvider = IntUiTabIcons.close(),
) = IntUiTabIcons(close)
