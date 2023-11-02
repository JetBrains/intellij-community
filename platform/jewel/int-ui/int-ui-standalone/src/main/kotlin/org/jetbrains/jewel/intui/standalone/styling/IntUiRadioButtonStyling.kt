package org.jetbrains.jewel.intui.standalone.styling

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import org.jetbrains.jewel.intui.core.theme.IntUiDarkTheme
import org.jetbrains.jewel.intui.core.theme.IntUiLightTheme
import org.jetbrains.jewel.intui.standalone.standalonePainterProvider
import org.jetbrains.jewel.ui.component.styling.RadioButtonColors
import org.jetbrains.jewel.ui.component.styling.RadioButtonIcons
import org.jetbrains.jewel.ui.component.styling.RadioButtonMetrics
import org.jetbrains.jewel.ui.component.styling.RadioButtonStyle
import org.jetbrains.jewel.ui.painter.PainterProvider

@Composable
public fun RadioButtonStyle.Companion.light(
    colors: RadioButtonColors = RadioButtonColors.light(),
    metrics: RadioButtonMetrics = RadioButtonMetrics.defaults(),
    icons: RadioButtonIcons = RadioButtonIcons.light(),
): RadioButtonStyle =
    RadioButtonStyle(colors, metrics, icons)

@Composable
public fun RadioButtonStyle.Companion.dark(
    colors: RadioButtonColors = RadioButtonColors.dark(),
    metrics: RadioButtonMetrics = RadioButtonMetrics.defaults(),
    icons: RadioButtonIcons = RadioButtonIcons.dark(),
): RadioButtonStyle =
    RadioButtonStyle(colors, metrics, icons)

@Composable
public fun RadioButtonColors.Companion.light(
    content: Color = Color.Unspecified,
    contentHovered: Color = content,
    contentDisabled: Color = IntUiLightTheme.colors.grey(8),
    contentSelected: Color = content,
    contentSelectedHovered: Color = content,
    contentSelectedDisabled: Color = contentDisabled,
): RadioButtonColors =
    RadioButtonColors(
        content = content,
        contentHovered = contentHovered,
        contentDisabled = contentDisabled,
        contentSelected = contentSelected,
        contentSelectedHovered = contentSelectedHovered,
        contentSelectedDisabled = contentSelectedDisabled,
    )

@Composable
public fun RadioButtonColors.Companion.dark(
    content: Color = Color.Unspecified,
    contentHovered: Color = content,
    contentDisabled: Color = IntUiDarkTheme.colors.grey(8),
    contentSelected: Color = content,
    contentSelectedHovered: Color = content,
    contentSelectedDisabled: Color = contentDisabled,
): RadioButtonColors =
    RadioButtonColors(
        content = content,
        contentHovered = contentHovered,
        contentDisabled = contentDisabled,
        contentSelected = contentSelected,
        contentSelectedHovered = contentSelectedHovered,
        contentSelectedDisabled = contentSelectedDisabled,
    )

public fun RadioButtonMetrics.Companion.defaults(
    radioButtonSize: DpSize = DpSize(19.dp, 19.dp),
    iconContentGap: Dp = 8.dp,
): RadioButtonMetrics =
    RadioButtonMetrics(radioButtonSize, iconContentGap)

public fun RadioButtonIcons.Companion.light(
    radioButton: PainterProvider =
        standalonePainterProvider("com/intellij/ide/ui/laf/icons/intellij/radio.svg"),
): RadioButtonIcons =
    RadioButtonIcons(radioButton)

public fun RadioButtonIcons.Companion.dark(
    radioButton: PainterProvider =
        standalonePainterProvider("com/intellij/ide/ui/laf/icons/darcula/radio.svg"),
): RadioButtonIcons =
    RadioButtonIcons(radioButton)
