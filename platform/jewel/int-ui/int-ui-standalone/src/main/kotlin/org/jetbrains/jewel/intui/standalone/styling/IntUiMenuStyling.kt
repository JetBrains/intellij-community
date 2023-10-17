package org.jetbrains.jewel.intui.standalone.styling

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import org.jetbrains.jewel.intui.core.theme.IntUiDarkTheme
import org.jetbrains.jewel.intui.core.theme.IntUiLightTheme
import org.jetbrains.jewel.intui.standalone.standalonePainterProvider
import org.jetbrains.jewel.painter.PainterProvider
import org.jetbrains.jewel.styling.MenuColors
import org.jetbrains.jewel.styling.MenuIcons
import org.jetbrains.jewel.styling.MenuItemColors
import org.jetbrains.jewel.styling.MenuItemMetrics
import org.jetbrains.jewel.styling.MenuMetrics
import org.jetbrains.jewel.styling.MenuStyle
import org.jetbrains.jewel.styling.SubmenuMetrics

@Stable
data class IntUiMenuStyle(
    override val colors: IntUiMenuColors,
    override val metrics: IntUiMenuMetrics,
    override val icons: IntUiMenuIcons,
) : MenuStyle {

    companion object {

        @Composable
        fun light(
            colors: IntUiMenuColors = IntUiMenuColors.light(),
            metrics: IntUiMenuMetrics = IntUiMenuMetrics(),
            icons: IntUiMenuIcons = intUiMenuIcons(),
        ) = IntUiMenuStyle(colors, metrics, icons)

        @Composable
        fun dark(
            colors: IntUiMenuColors = IntUiMenuColors.dark(),
            metrics: IntUiMenuMetrics = IntUiMenuMetrics(),
            icons: IntUiMenuIcons = intUiMenuIcons(),
        ) = IntUiMenuStyle(colors, metrics, icons)
    }
}

@Immutable
data class IntUiMenuColors(
    override val background: Color,
    override val border: Color,
    override val shadow: Color,
    override val itemColors: IntUiMenuItemColors,
) : MenuColors {

    companion object {

        @Composable
        fun light(
            background: Color = IntUiLightTheme.colors.grey(14),
            border: Color = IntUiLightTheme.colors.grey(9),
            shadow: Color = Color(0x78919191), // Not a palette color
            itemColors: IntUiMenuItemColors = IntUiMenuItemColors.light(),
        ) = IntUiMenuColors(background, border, shadow, itemColors)

        @Composable
        fun dark(
            background: Color = IntUiDarkTheme.colors.grey(2),
            border: Color = IntUiDarkTheme.colors.grey(3),
            shadow: Color = Color(0x66000000), // Not a palette color
            itemColors: IntUiMenuItemColors = IntUiMenuItemColors.dark(),
        ) = IntUiMenuColors(background, border, shadow, itemColors)
    }
}

@Immutable
data class IntUiMenuItemColors(
    override val background: Color,
    override val backgroundDisabled: Color,
    override val backgroundFocused: Color,
    override val backgroundPressed: Color,
    override val backgroundHovered: Color,
    override val content: Color,
    override val contentDisabled: Color,
    override val contentFocused: Color,
    override val contentPressed: Color,
    override val contentHovered: Color,
    override val iconTint: Color,
    override val iconTintDisabled: Color,
    override val iconTintFocused: Color,
    override val iconTintPressed: Color,
    override val iconTintHovered: Color,
    override val separator: Color,
) : MenuItemColors {

    companion object {

        @Composable
        fun light(
            background: Color = IntUiLightTheme.colors.grey(14),
            backgroundDisabled: Color = IntUiLightTheme.colors.grey(14),
            backgroundFocused: Color = IntUiLightTheme.colors.blue(11),
            backgroundPressed: Color = background,
            backgroundHovered: Color = backgroundFocused,
            content: Color = IntUiLightTheme.colors.grey(1),
            contentDisabled: Color = IntUiLightTheme.colors.grey(8),
            contentFocused: Color = content,
            contentPressed: Color = content,
            contentHovered: Color = content,
            iconTint: Color = IntUiLightTheme.colors.grey(7),
            iconTintDisabled: Color = iconTint,
            iconTintFocused: Color = iconTint,
            iconTintPressed: Color = iconTint,
            iconTintHovered: Color = iconTint,
            separator: Color = IntUiLightTheme.colors.grey(12),
        ) = IntUiMenuItemColors(
            background,
            backgroundDisabled,
            backgroundFocused,
            backgroundPressed,
            backgroundHovered,
            content,
            contentDisabled,
            contentFocused,
            contentPressed,
            contentHovered,
            iconTint,
            iconTintDisabled,
            iconTintFocused,
            iconTintPressed,
            iconTintHovered,
            separator,
        )

        @Composable
        fun dark(
            background: Color = IntUiDarkTheme.colors.grey(2),
            backgroundDisabled: Color = IntUiDarkTheme.colors.grey(2),
            backgroundFocused: Color = IntUiDarkTheme.colors.blue(2),
            backgroundPressed: Color = background,
            backgroundHovered: Color = background,
            content: Color = IntUiDarkTheme.colors.grey(12),
            contentDisabled: Color = IntUiDarkTheme.colors.grey(7),
            contentFocused: Color = content,
            contentPressed: Color = content,
            contentHovered: Color = content,
            iconTint: Color = IntUiDarkTheme.colors.grey(10),
            iconTintDisabled: Color = iconTint,
            iconTintFocused: Color = iconTint,
            iconTintPressed: Color = iconTint,
            iconTintHovered: Color = iconTint,
            separator: Color = IntUiDarkTheme.colors.grey(3),
        ) = IntUiMenuItemColors(
            background,
            backgroundDisabled,
            backgroundFocused,
            backgroundPressed,
            backgroundHovered,
            content,
            contentDisabled,
            contentFocused,
            contentPressed,
            contentHovered,
            iconTint,
            iconTintDisabled,
            iconTintFocused,
            iconTintPressed,
            iconTintHovered,
            separator,
        )
    }
}

@Stable
data class IntUiMenuMetrics(
    override val cornerSize: CornerSize = CornerSize(8.dp),
    override val menuMargin: PaddingValues = PaddingValues(vertical = 6.dp),
    override val contentPadding: PaddingValues = PaddingValues(vertical = 8.dp),
    override val offset: DpOffset = DpOffset((-6).dp, 2.dp),
    override val shadowSize: Dp = 12.dp,
    override val borderWidth: Dp = 1.dp,
    override val itemMetrics: MenuItemMetrics = IntUiMenuItemMetrics(),
    override val submenuMetrics: SubmenuMetrics = IntUiSubmenuMetrics(),
) : MenuMetrics

@Stable
data class IntUiMenuItemMetrics(
    override val selectionCornerSize: CornerSize = CornerSize(4.dp),
    override val outerPadding: PaddingValues = PaddingValues(horizontal = 4.dp),
    override val contentPadding: PaddingValues = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
    override val separatorPadding: PaddingValues = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
    override val separatorThickness: Dp = 1.dp,
) : MenuItemMetrics

@Stable
data class IntUiSubmenuMetrics(
    override val offset: DpOffset = DpOffset(0.dp, (-8).dp),
) : SubmenuMetrics

@Immutable
data class IntUiMenuIcons(
    override val submenuChevron: PainterProvider,
) : MenuIcons {

    companion object {

        @Composable
        fun submenuChevron(
            basePath: String = "expui/general/chevronRight.svg",
        ): PainterProvider = standalonePainterProvider(basePath)
    }
}

@Composable
fun intUiMenuIcons(
    submenuChevron: PainterProvider = IntUiMenuIcons.submenuChevron(),
) =
    IntUiMenuIcons(submenuChevron)
