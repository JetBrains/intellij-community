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
import org.jetbrains.jewel.ui.component.styling.CheckboxColors
import org.jetbrains.jewel.ui.component.styling.CheckboxIcons
import org.jetbrains.jewel.ui.component.styling.CheckboxMetrics
import org.jetbrains.jewel.ui.component.styling.CheckboxStyle
import org.jetbrains.jewel.ui.painter.PainterProvider

@Composable
public fun CheckboxStyle.Companion.light(
    colors: CheckboxColors = CheckboxColors.light(),
    metrics: CheckboxMetrics = CheckboxMetrics.defaults(),
    icons: CheckboxIcons = CheckboxIcons.light(),
): CheckboxStyle =
    CheckboxStyle(colors, metrics, icons)

@Composable
public fun CheckboxStyle.Companion.dark(
    colors: CheckboxColors = CheckboxColors.dark(),
    metrics: CheckboxMetrics = CheckboxMetrics.defaults(),
    icons: CheckboxIcons = CheckboxIcons.dark(),
): CheckboxStyle =
    CheckboxStyle(colors, metrics, icons)

@Composable
public fun CheckboxColors.Companion.light(
    content: Color = Color.Unspecified,
    contentDisabled: Color = IntUiLightTheme.colors.grey(8),
    contentSelected: Color = content,
): CheckboxColors =
    CheckboxColors(content, contentDisabled, contentSelected)

@Composable
public fun CheckboxColors.Companion.dark(
    content: Color = Color.Unspecified,
    contentDisabled: Color = IntUiDarkTheme.colors.grey(7),
    contentSelected: Color = content,
): CheckboxColors =
    CheckboxColors(content, contentDisabled, contentSelected)

public fun CheckboxMetrics.Companion.defaults(
    checkboxSize: DpSize = DpSize(19.dp, 19.dp),
    checkboxCornerSize: CornerSize = CornerSize(3.dp),
    outlineSize: DpSize = DpSize(15.dp, 15.dp),
    outlineOffset: DpOffset = DpOffset(2.5.dp, 1.5.dp),
    iconContentGap: Dp = 5.dp,
): CheckboxMetrics =
    CheckboxMetrics(
        checkboxSize,
        checkboxCornerSize,
        outlineSize,
        outlineOffset,
        iconContentGap,
    )

@Composable
public fun CheckboxIcons.Companion.light(
    checkbox: PainterProvider = standalonePainterProvider("com/intellij/ide/ui/laf/icons/intellij/checkBox.svg"),
): CheckboxIcons =
    CheckboxIcons(checkbox)

@Composable
public fun CheckboxIcons.Companion.dark(
    checkbox: PainterProvider = standalonePainterProvider("com/intellij/ide/ui/laf/icons/darcula/checkBox.svg"),
): CheckboxIcons =
    CheckboxIcons(checkbox)
