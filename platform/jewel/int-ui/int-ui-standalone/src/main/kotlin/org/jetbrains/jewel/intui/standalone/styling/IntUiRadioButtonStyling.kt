package org.jetbrains.jewel.intui.standalone.styling

import androidx.compose.runtime.Composable
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

@Composable
fun RadioButtonStyle.Companion.light(
    colors: RadioButtonColors = RadioButtonColors.light(),
    metrics: RadioButtonMetrics = RadioButtonMetrics.defaults(),
    icons: RadioButtonIcons = RadioButtonIcons.light(),
) = RadioButtonStyle(colors, metrics, icons)

@Composable
fun RadioButtonStyle.Companion.dark(
    colors: RadioButtonColors = RadioButtonColors.dark(),
    metrics: RadioButtonMetrics = RadioButtonMetrics.defaults(),
    icons: RadioButtonIcons = RadioButtonIcons.dark(),
) = RadioButtonStyle(colors, metrics, icons)

@Composable
fun RadioButtonColors.Companion.light(
    content: Color = Color.Unspecified,
    contentHovered: Color = content,
    contentDisabled: Color = IntUiLightTheme.colors.grey(8),
    contentSelected: Color = content,
    contentSelectedHovered: Color = content,
    contentSelectedDisabled: Color = contentDisabled,
) = RadioButtonColors(
    content,
    contentHovered,
    contentDisabled,
    contentSelected,
    contentSelectedHovered,
    contentSelectedDisabled,
)

@Composable
fun RadioButtonColors.Companion.dark(
    content: Color = Color.Unspecified,
    contentHovered: Color = content,
    contentDisabled: Color = IntUiDarkTheme.colors.grey(8),
    contentSelected: Color = content,
    contentSelectedHovered: Color = content,
    contentSelectedDisabled: Color = contentDisabled,
) = RadioButtonColors(
    content,
    contentHovered,
    contentDisabled,
    contentSelected,
    contentSelectedHovered,
    contentSelectedDisabled,
)

fun RadioButtonMetrics.Companion.defaults(
    radioButtonSize: DpSize = DpSize(19.dp, 19.dp),
    iconContentGap: Dp = 8.dp,
) = RadioButtonMetrics(radioButtonSize, iconContentGap)

fun RadioButtonIcons.Companion.light(
    radioButton: PainterProvider = standalonePainterProvider("com/intellij/ide/ui/laf/icons/intellij/radio.svg"),
) = RadioButtonIcons(radioButton)

fun RadioButtonIcons.Companion.dark(
    radioButton: PainterProvider = standalonePainterProvider("com/intellij/ide/ui/laf/icons/darcula/radio.svg"),
) = RadioButtonIcons(radioButton)
