package org.jetbrains.jewel.themes.intui.standalone.styling

import androidx.compose.foundation.shape.CornerSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import org.jetbrains.jewel.CheckboxState
import org.jetbrains.jewel.SvgLoader
import org.jetbrains.jewel.styling.CheckboxColors
import org.jetbrains.jewel.styling.CheckboxIcons
import org.jetbrains.jewel.styling.CheckboxMetrics
import org.jetbrains.jewel.styling.CheckboxStyle
import org.jetbrains.jewel.styling.PainterProvider
import org.jetbrains.jewel.styling.ResourcePainterProvider
import org.jetbrains.jewel.styling.StatefulResourcePathPatcher
import org.jetbrains.jewel.themes.intui.core.theme.IntUiDarkTheme
import org.jetbrains.jewel.themes.intui.core.theme.IntUiLightTheme

@Immutable
data class IntUiCheckboxStyle(
    override val colors: IntUiCheckboxColors,
    override val metrics: IntUiCheckboxMetrics,
    override val icons: IntUiCheckboxIcons,
) : CheckboxStyle {

    companion object {

        @Composable
        fun light(
            svgLoader: SvgLoader,
            colors: IntUiCheckboxColors = IntUiCheckboxColors.light(),
            metrics: IntUiCheckboxMetrics = IntUiCheckboxMetrics(),
            icons: IntUiCheckboxIcons = intUiCheckboxIcons(svgLoader),
        ) = IntUiCheckboxStyle(colors, metrics, icons)

        @Composable
        fun dark(
            svgLoader: SvgLoader,
            colors: IntUiCheckboxColors = IntUiCheckboxColors.dark(),
            metrics: IntUiCheckboxMetrics = IntUiCheckboxMetrics(),
            icons: IntUiCheckboxIcons = intUiCheckboxIcons(svgLoader),
        ) = IntUiCheckboxStyle(colors, metrics, icons)
    }
}

@Immutable
data class IntUiCheckboxColors(
    override val checkboxBackground: Color,
    override val checkboxBackgroundDisabled: Color,
    override val checkboxBackgroundSelected: Color,
    override val content: Color,
    override val contentDisabled: Color,
    override val contentSelected: Color,
) : CheckboxColors {

    companion object {

        @Composable
        fun light(
            background: Color = IntUiLightTheme.colors.grey(14),
            backgroundDisabled: Color = IntUiLightTheme.colors.grey(13),
            backgroundSelected: Color = IntUiLightTheme.colors.blue(4),
            content: Color = IntUiLightTheme.colors.grey(1),
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

        @Composable
        fun dark(
            background: Color = Color.Unspecified,
            backgroundDisabled: Color = IntUiDarkTheme.colors.grey(3),
            backgroundSelected: Color = IntUiDarkTheme.colors.blue(6),
            content: Color = IntUiDarkTheme.colors.grey(12),
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

@Immutable
data class IntUiCheckboxMetrics(
    override val checkboxSize: DpSize = DpSize(19.dp, 19.dp),
    override val checkboxCornerSize: CornerSize = CornerSize(3.dp),
    override val outlineSize: DpSize = DpSize(15.dp, 15.dp),
    override val outlineOffset: DpOffset = DpOffset(2.5.dp, 1.5.dp),
    override val iconContentGap: Dp = 5.dp,
) : CheckboxMetrics

@Immutable
data class IntUiCheckboxIcons(
    override val checkbox: PainterProvider<CheckboxState>,
) : CheckboxIcons {

    companion object {

        fun checkbox(
            svgLoader: SvgLoader,
            basePath: String = "icons/intui/checkBox.svg",
        ): PainterProvider<CheckboxState> =
            ResourcePainterProvider.stateful(
                basePath,
                svgLoader,
                pathPatcher = StatefulResourcePathPatcher(
                    prefixTokensProvider = { state: CheckboxState ->
                        if (state.toggleableState == ToggleableState.Indeterminate) "Indeterminate" else ""
                    },
                ),
            )
    }
}

fun intUiCheckboxIcons(
    svgLoader: SvgLoader,
    checkbox: PainterProvider<CheckboxState> = IntUiCheckboxIcons.checkbox(svgLoader),
) = IntUiCheckboxIcons(checkbox)
