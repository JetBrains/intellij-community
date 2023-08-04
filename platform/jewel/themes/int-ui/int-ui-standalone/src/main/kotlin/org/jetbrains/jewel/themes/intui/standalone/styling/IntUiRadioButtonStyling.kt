package org.jetbrains.jewel.themes.intui.standalone.styling

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import org.jetbrains.jewel.RadioButtonState
import org.jetbrains.jewel.SvgLoader
import org.jetbrains.jewel.styling.RadioButtonButtonColors
import org.jetbrains.jewel.styling.RadioButtonColors
import org.jetbrains.jewel.styling.RadioButtonIcons
import org.jetbrains.jewel.styling.RadioButtonMetrics
import org.jetbrains.jewel.styling.RadioButtonStyle
import org.jetbrains.jewel.styling.ResourcePainterProvider
import org.jetbrains.jewel.styling.StatefulPainterProvider
import org.jetbrains.jewel.themes.intui.core.theme.IntUiDarkTheme
import org.jetbrains.jewel.themes.intui.core.theme.IntUiLightTheme
import org.jetbrains.jewel.themes.intui.standalone.IntUiTheme

@Immutable
data class IntUiRadioButtonStyle(
    override val colors: IntUiRadioButtonColors,
    override val metrics: IntUiRadioButtonMetrics,
    override val icons: IntUiRadioButtonIcons,
) : RadioButtonStyle {

    companion object {

        @Composable
        fun light(
            svgLoader: SvgLoader,
            colors: IntUiRadioButtonColors = IntUiRadioButtonColors.light(),
            metrics: IntUiRadioButtonMetrics = IntUiRadioButtonMetrics(),
            icons: IntUiRadioButtonIcons = intUiRadioButtonIcons(svgLoader),
        ) = IntUiRadioButtonStyle(colors, metrics, icons)

        @Composable
        fun dark(
            svgLoader: SvgLoader,
            colors: IntUiRadioButtonColors = IntUiRadioButtonColors.dark(),
            metrics: IntUiRadioButtonMetrics = IntUiRadioButtonMetrics(),
            icons: IntUiRadioButtonIcons = intUiRadioButtonIcons(svgLoader),
        ) = IntUiRadioButtonStyle(colors, metrics, icons)
    }
}

@Immutable
data class IntUiRadioButtonColors(
    override val content: Color,
    override val contentHovered: Color,
    override val contentDisabled: Color,
    override val contentSelected: Color,
    override val contentSelectedHovered: Color,
    override val contentSelectedDisabled: Color,
    override val buttonColors: IntUiRadioButtonButtonColors,
) : RadioButtonColors {

    companion object {

        @Composable
        fun light(
            content: Color = IntUiTheme.defaultLightTextStyle.color,
            contentHovered: Color = IntUiLightTheme.colors.grey(8),
            contentDisabled: Color = content,
            contentSelected: Color = content,
            contentSelectedHovered: Color = content,
            contentSelectedDisabled: Color = content,
            buttonColors: IntUiRadioButtonButtonColors = IntUiRadioButtonButtonColors.light(),
        ) = IntUiRadioButtonColors(
            content,
            contentHovered,
            contentDisabled,
            contentSelected,
            contentSelectedHovered,
            contentSelectedDisabled,
            buttonColors
        )

        @Composable
        fun dark(
            content: Color = IntUiTheme.defaultDarkTextStyle.color,
            contentHovered: Color = IntUiDarkTheme.colors.grey(8),
            contentDisabled: Color = content,
            contentSelected: Color = content,
            contentSelectedHovered: Color = content,
            contentSelectedDisabled: Color = content,
            buttonColors: IntUiRadioButtonButtonColors = IntUiRadioButtonButtonColors.dark(),
        ) = IntUiRadioButtonColors(
            content,
            contentHovered,
            contentDisabled,
            contentSelected,
            contentSelectedHovered,
            contentSelectedDisabled,
            buttonColors
        )
    }
}

@Immutable
data class IntUiRadioButtonButtonColors(
    override val fill: Color,
    override val fillHovered: Color,
    override val fillDisabled: Color,
    override val fillSelected: Color,
    override val fillSelectedHovered: Color,
    override val fillSelectedDisabled: Color,
    override val border: Color,
    override val borderHovered: Color,
    override val borderDisabled: Color,
    override val borderSelected: Color,
    override val borderSelectedHovered: Color,
    override val borderSelectedDisabled: Color,
    override val markSelected: Color,
    override val markSelectedHovered: Color,
    override val markSelectedDisabled: Color,
) : RadioButtonButtonColors {

    companion object {

        @Composable
        fun light(
            fill: Color = IntUiLightTheme.colors.grey(14),
            fillHovered: Color = fill,
            fillDisabled: Color = IntUiLightTheme.colors.grey(13),
            fillSelected: Color = IntUiLightTheme.colors.blue(4),
            fillSelectedHovered: Color = IntUiLightTheme.colors.blue(3),
            fillSelectedDisabled: Color = fillDisabled,
            border: Color = IntUiLightTheme.colors.grey(8),
            borderHovered: Color = IntUiLightTheme.colors.grey(6),
            borderDisabled: Color = IntUiLightTheme.colors.grey(11),
            borderSelected: Color = Color.Unspecified,
            borderSelectedHovered: Color = borderSelected,
            borderSelectedDisabled: Color = borderDisabled,
            markSelected: Color = IntUiLightTheme.colors.grey(14),
            markSelectedHovered: Color = markSelected,
            markSelectedDisabled: Color = IntUiLightTheme.colors.grey(9),
        ) = IntUiRadioButtonButtonColors(
            fill,
            fillHovered,
            fillDisabled,
            fillSelected,
            fillSelectedHovered,
            fillSelectedDisabled,
            border,
            borderHovered,
            borderDisabled,
            borderSelected,
            borderSelectedHovered,
            borderSelectedDisabled,
            markSelected,
            markSelectedHovered,
            markSelectedDisabled
        )

        @Composable
        fun dark(
            fill: Color = Color.Unspecified,
            fillHovered: Color = fill,
            fillDisabled: Color = IntUiDarkTheme.colors.grey(3),
            fillSelected: Color = IntUiDarkTheme.colors.blue(6),
            fillSelectedHovered: Color = IntUiDarkTheme.colors.blue(5),
            fillSelectedDisabled: Color = fillDisabled,
            border: Color = IntUiDarkTheme.colors.grey(6),
            borderHovered: Color = IntUiDarkTheme.colors.grey(9),
            borderDisabled: Color = IntUiDarkTheme.colors.grey(6),
            borderSelected: Color = Color.Unspecified,
            borderSelectedHovered: Color = borderSelected,
            borderSelectedDisabled: Color = borderDisabled,
            markSelected: Color = IntUiDarkTheme.colors.grey(14),
            markSelectedHovered: Color = markSelected,
            markSelectedDisabled: Color = IntUiDarkTheme.colors.grey(7),
        ) = IntUiRadioButtonButtonColors(
            fill,
            fillHovered,
            fillDisabled,
            fillSelected,
            fillSelectedHovered,
            fillSelectedDisabled,
            border,
            borderHovered,
            borderDisabled,
            borderSelected,
            borderSelectedHovered,
            borderSelectedDisabled,
            markSelected,
            markSelectedHovered,
            markSelectedDisabled
        )
    }
}

@Immutable
data class IntUiRadioButtonMetrics(
    override val radioButtonSize: DpSize = DpSize(16.dp, 16.dp),
    override val iconContentGap: Dp = 8.dp,
) : RadioButtonMetrics

@Immutable
data class IntUiRadioButtonIcons(
    override val radioButton: StatefulPainterProvider<RadioButtonState>,
) : RadioButtonIcons {

    companion object {

        @Composable
        fun radioButton(
            svgLoader: SvgLoader,
            basePath: String = "icons/intui/radio.svg",
        ): StatefulPainterProvider<RadioButtonState> =
            ResourcePainterProvider(basePath, svgLoader)
    }
}

@Composable
fun intUiRadioButtonIcons(
    svgLoader: SvgLoader,
    radioButton: StatefulPainterProvider<RadioButtonState> = IntUiRadioButtonIcons.radioButton(svgLoader),
) = IntUiRadioButtonIcons(radioButton)
