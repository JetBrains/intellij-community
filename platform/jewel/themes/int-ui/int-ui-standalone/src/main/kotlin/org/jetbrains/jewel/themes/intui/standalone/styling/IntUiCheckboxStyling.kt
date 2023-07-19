package org.jetbrains.jewel.themes.intui.standalone.styling

import androidx.compose.foundation.shape.CornerSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import org.jetbrains.jewel.CheckboxState
import org.jetbrains.jewel.SvgPatcher
import org.jetbrains.jewel.styling.CheckboxColors
import org.jetbrains.jewel.styling.CheckboxIcons
import org.jetbrains.jewel.styling.CheckboxMetrics
import org.jetbrains.jewel.styling.CheckboxStyle
import org.jetbrains.jewel.styling.ResourcePathPainterProvider
import org.jetbrains.jewel.styling.StatefulPainterProvider
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
            svgPatcher: SvgPatcher,
            colors: IntUiCheckboxColors = IntUiCheckboxColors.light(),
            metrics: IntUiCheckboxMetrics = IntUiCheckboxMetrics(),
            icons: IntUiCheckboxIcons = intUiCheckboxIcons(svgPatcher),
        ) = IntUiCheckboxStyle(colors, metrics, icons)

        @Composable
        fun dark(
            svgPatcher: SvgPatcher,
            colors: IntUiCheckboxColors = IntUiCheckboxColors.dark(),
            metrics: IntUiCheckboxMetrics = IntUiCheckboxMetrics(),
            icons: IntUiCheckboxIcons = intUiCheckboxIcons(svgPatcher),
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
    override val checkboxBorder: Color,
    override val checkboxBorderDisabled: Color,
    override val checkboxBorderSelected: Color,
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
            checkboxBorder: Color = IntUiLightTheme.colors.grey(8),
            borderDisabled: Color = IntUiLightTheme.colors.grey(11),
            borderSelected: Color = IntUiLightTheme.colors.blue(4),
        ) = IntUiCheckboxColors(
            background,
            backgroundDisabled,
            backgroundSelected,
            content,
            contentDisabled,
            contentSelected,
            checkboxBorder,
            borderDisabled,
            borderSelected
        )

        @Composable
        fun dark(
            background: Color = Color.Unspecified,
            backgroundDisabled: Color = IntUiDarkTheme.colors.grey(3),
            backgroundSelected: Color = IntUiDarkTheme.colors.blue(6),
            content: Color = IntUiDarkTheme.colors.grey(12),
            contentDisabled: Color = IntUiDarkTheme.colors.grey(7),
            contentSelected: Color = content,
            checkboxBorder: Color = IntUiDarkTheme.colors.grey(6),
            borderDisabled: Color = IntUiDarkTheme.colors.grey(6),
            borderSelected: Color = Color.Unspecified,
        ) = IntUiCheckboxColors(
            background,
            backgroundDisabled,
            backgroundSelected,
            content,
            contentDisabled,
            contentSelected,
            checkboxBorder,
            borderDisabled,
            borderSelected
        )
    }
}

@Immutable
data class IntUiCheckboxMetrics(
    override val checkboxSize: DpSize = DpSize(20.dp, 20.dp),
    override val checkboxCornerSize: CornerSize = CornerSize(3.dp),
    override val outlineWidth: Dp = 3.dp,
    override val iconContentGap: Dp = 4.dp,
) : CheckboxMetrics

@Immutable
data class IntUiCheckboxIcons(
    override val checkbox: StatefulPainterProvider<CheckboxState>,
) : CheckboxIcons {

    companion object {

        @Composable
        fun checkbox(
            svgPatcher: SvgPatcher,
            basePath: String = "icons/intui/checkBox.svg",
        ) =
            ResourcePathPainterProvider(
                basePath,
                svgPatcher,
                prefixTokensProvider = { state: CheckboxState ->
                    if (state.toggleableState == ToggleableState.Indeterminate) "Indeterminate" else ""
                }
            )
    }
}

@Composable
fun intUiCheckboxIcons(
    svgPatcher: SvgPatcher,
    checkbox: StatefulPainterProvider<CheckboxState> = IntUiCheckboxIcons.checkbox(svgPatcher),
) = IntUiCheckboxIcons(checkbox)
