package org.jetbrains.jewel.intui.standalone.styling

import androidx.compose.foundation.shape.CornerSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import org.jetbrains.jewel.intui.core.theme.IntUiDarkTheme
import org.jetbrains.jewel.intui.core.theme.IntUiLightTheme
import org.jetbrains.jewel.intui.standalone.standalonePainterProvider
import org.jetbrains.jewel.painter.PainterProvider
import org.jetbrains.jewel.styling.CheckboxColors
import org.jetbrains.jewel.styling.CheckboxIcons
import org.jetbrains.jewel.styling.CheckboxMetrics
import org.jetbrains.jewel.styling.CheckboxStyle

@Immutable data class IntUiCheckboxStyle(
    override val colors: IntUiCheckboxColors,
    override val metrics: IntUiCheckboxMetrics,
    override val icons: IntUiCheckboxIcons,
) : CheckboxStyle {

    companion object {

        @Composable fun light(
            colors: IntUiCheckboxColors = IntUiCheckboxColors.light(),
            metrics: IntUiCheckboxMetrics = IntUiCheckboxMetrics(),
            icons: IntUiCheckboxIcons = IntUiCheckboxIcons.light(),
        ) = IntUiCheckboxStyle(colors, metrics, icons)

        @Composable fun dark(
            colors: IntUiCheckboxColors = IntUiCheckboxColors.dark(),
            metrics: IntUiCheckboxMetrics = IntUiCheckboxMetrics(),
            icons: IntUiCheckboxIcons = IntUiCheckboxIcons.dark(),
        ) = IntUiCheckboxStyle(colors, metrics, icons)
    }
}

@Immutable data class IntUiCheckboxColors(
    override val checkboxBackground: Color,
    override val checkboxBackgroundDisabled: Color,
    override val checkboxBackgroundSelected: Color,
    override val content: Color,
    override val contentDisabled: Color,
    override val contentSelected: Color,
) : CheckboxColors {

    companion object {

        @Composable fun light(
            background: Color = IntUiLightTheme.colors.grey(14),
            backgroundDisabled: Color = IntUiLightTheme.colors.grey(13),
            backgroundSelected: Color = IntUiLightTheme.colors.blue(4),
            content: Color = Color.Unspecified,
            contentDisabled: Color = IntUiLightTheme.colors.grey(8),
            contentSelected: Color = content,
        ) = IntUiCheckboxColors(
            background,
            backgroundDisabled,
            backgroundSelected,
            content,
            contentDisabled,
            contentSelected,
        )

        @Composable fun dark(
            background: Color = Color.Unspecified,
            backgroundDisabled: Color = IntUiDarkTheme.colors.grey(3),
            backgroundSelected: Color = IntUiDarkTheme.colors.blue(6),
            content: Color = Color.Unspecified,
            contentDisabled: Color = IntUiDarkTheme.colors.grey(7),
            contentSelected: Color = content,
        ) = IntUiCheckboxColors(
            background,
            backgroundDisabled,
            backgroundSelected,
            content,
            contentDisabled,
            contentSelected,
        )
    }
}

@Immutable data class IntUiCheckboxMetrics(
    override val checkboxSize: DpSize = DpSize(19.dp, 19.dp),
    override val checkboxCornerSize: CornerSize = CornerSize(3.dp),
    override val outlineSize: DpSize = DpSize(15.dp, 15.dp),
    override val outlineOffset: DpOffset = DpOffset(2.5.dp, 1.5.dp),
    override val iconContentGap: Dp = 5.dp,
) : CheckboxMetrics

@Immutable data class IntUiCheckboxIcons(
    override val checkbox: PainterProvider,
) : CheckboxIcons {

    companion object {

        @Composable
        fun checkbox(
            basePath: String = "com/intellij/ide/ui/laf/icons/intellij/checkBox.svg",
        ): PainterProvider = standalonePainterProvider(basePath)

        @Composable
        fun light(
            checkbox: PainterProvider = checkbox("com/intellij/ide/ui/laf/icons/intellij/checkBox.svg"),
        ) = IntUiCheckboxIcons(checkbox)

        @Composable
        fun dark(
            checkbox: PainterProvider = checkbox("com/intellij/ide/ui/laf/icons/darcula/checkBox.svg"),
        ) = IntUiCheckboxIcons(checkbox)
    }
}
