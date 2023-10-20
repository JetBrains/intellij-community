package org.jetbrains.jewel.intui.standalone.styling

import androidx.compose.foundation.shape.CornerSize
import androidx.compose.runtime.Composable
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

@Composable
fun CheckboxStyle.Companion.light(
    colors: CheckboxColors = CheckboxColors.light(),
    metrics: CheckboxMetrics = CheckboxMetrics.defaults(),
    icons: CheckboxIcons = CheckboxIcons.light(),
) = CheckboxStyle(colors, metrics, icons)

@Composable
fun CheckboxStyle.Companion.dark(
    colors: CheckboxColors = CheckboxColors.dark(),
    metrics: CheckboxMetrics = CheckboxMetrics.defaults(),
    icons: CheckboxIcons = CheckboxIcons.dark(),
) = CheckboxStyle(colors, metrics, icons)

@Composable
fun CheckboxColors.Companion.light(
    content: Color = Color.Unspecified,
    contentDisabled: Color = IntUiLightTheme.colors.grey(8),
    contentSelected: Color = content,
) = CheckboxColors(content, contentDisabled, contentSelected)

@Composable
fun CheckboxColors.Companion.dark(
    content: Color = Color.Unspecified,
    contentDisabled: Color = IntUiDarkTheme.colors.grey(7),
    contentSelected: Color = content,
) = CheckboxColors(content, contentDisabled, contentSelected)

fun CheckboxMetrics.Companion.defaults(
    checkboxSize: DpSize = DpSize(19.dp, 19.dp),
    checkboxCornerSize: CornerSize = CornerSize(3.dp),
    outlineSize: DpSize = DpSize(15.dp, 15.dp),
    outlineOffset: DpOffset = DpOffset(2.5.dp, 1.5.dp),
    iconContentGap: Dp = 5.dp,
) = CheckboxMetrics(checkboxSize, checkboxCornerSize, outlineSize, outlineOffset, iconContentGap)

@Composable
fun CheckboxIcons.Companion.light(
    checkbox: PainterProvider = checkbox("com/intellij/ide/ui/laf/icons/intellij/checkBox.svg"),
) = CheckboxIcons(checkbox)

@Composable
fun CheckboxIcons.Companion.dark(
    checkbox: PainterProvider = checkbox("com/intellij/ide/ui/laf/icons/darcula/checkBox.svg"),
) = CheckboxIcons(checkbox)

@Composable
private fun checkbox(basePath: String): PainterProvider =
    standalonePainterProvider(basePath)
