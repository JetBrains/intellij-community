package org.jetbrains.jewel.intui.standalone.styling

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import org.jetbrains.jewel.intui.core.theme.IntUiDarkTheme
import org.jetbrains.jewel.intui.core.theme.IntUiLightTheme
import org.jetbrains.jewel.intui.standalone.standalonePainterProvider
import org.jetbrains.jewel.painter.PainterProvider
import org.jetbrains.jewel.styling.RadioButtonColors
import org.jetbrains.jewel.styling.RadioButtonIcons
import org.jetbrains.jewel.styling.RadioButtonMetrics
import org.jetbrains.jewel.styling.RadioButtonStyle

@Immutable
data class IntUiRadioButtonStyle(
    override val colors: IntUiRadioButtonColors,
    override val metrics: IntUiRadioButtonMetrics,
    override val icons: IntUiRadioButtonIcons,
) : RadioButtonStyle {

    companion object {

        @Composable
        fun light(
            colors: IntUiRadioButtonColors = IntUiRadioButtonColors.light(),
            metrics: IntUiRadioButtonMetrics = IntUiRadioButtonMetrics(),
            icons: IntUiRadioButtonIcons = IntUiRadioButtonIcons.light(),
        ) = IntUiRadioButtonStyle(colors, metrics, icons)

        @Composable
        fun dark(
            colors: IntUiRadioButtonColors = IntUiRadioButtonColors.dark(),
            metrics: IntUiRadioButtonMetrics = IntUiRadioButtonMetrics(),
            icons: IntUiRadioButtonIcons = IntUiRadioButtonIcons.dark(),
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
) : RadioButtonColors {

    companion object {

        @Composable
        fun light(
            content: Color = Color.Unspecified,
            contentHovered: Color = content,
            contentDisabled: Color = IntUiLightTheme.colors.grey(8),
            contentSelected: Color = content,
            contentSelectedHovered: Color = content,
            contentSelectedDisabled: Color = contentDisabled,
        ) = IntUiRadioButtonColors(
            content,
            contentHovered,
            contentDisabled,
            contentSelected,
            contentSelectedHovered,
            contentSelectedDisabled,
        )

        @Composable
        fun dark(
            content: Color = Color.Unspecified,
            contentHovered: Color = content,
            contentDisabled: Color = IntUiDarkTheme.colors.grey(8),
            contentSelected: Color = content,
            contentSelectedHovered: Color = content,
            contentSelectedDisabled: Color = contentDisabled,
        ) = IntUiRadioButtonColors(
            content,
            contentHovered,
            contentDisabled,
            contentSelected,
            contentSelectedHovered,
            contentSelectedDisabled,
        )
    }
}

@Immutable
data class IntUiRadioButtonMetrics(
    override val radioButtonSize: DpSize = DpSize(19.dp, 19.dp),
    override val iconContentGap: Dp = 8.dp,
) : RadioButtonMetrics

@Immutable
data class IntUiRadioButtonIcons(
    override val radioButton: PainterProvider,
) : RadioButtonIcons {

    companion object {

        @Composable
        fun radioButton(
            basePath: String = "com/intellij/ide/ui/laf/icons/intellij/radio.svg",
        ): PainterProvider = standalonePainterProvider(basePath)

        @Composable
        fun light(
            radioButton: PainterProvider = radioButton("com/intellij/ide/ui/laf/icons/intellij/radio.svg"),
        ) = IntUiRadioButtonIcons(radioButton)

        @Composable
        fun dark(
            radioButton: PainterProvider = radioButton("com/intellij/ide/ui/laf/icons/darcula/radio.svg"),
        ) = IntUiRadioButtonIcons(radioButton)
    }
}
