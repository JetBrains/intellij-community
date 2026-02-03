package org.jetbrains.jewel.intui.standalone.styling

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.intui.core.theme.IntUiLightTheme
import org.jetbrains.jewel.ui.component.styling.SimpleListItemColors
import org.jetbrains.jewel.ui.component.styling.SimpleListItemMetrics
import org.jetbrains.jewel.ui.component.styling.SimpleListItemStyle

@Composable
public fun SimpleListItemStyle.Companion.default(): SimpleListItemStyle = if (JewelTheme.isDark) dark() else light()

public fun SimpleListItemStyle.Companion.light(
    colors: SimpleListItemColors = SimpleListItemColors.light(),
    metrics: SimpleListItemMetrics = SimpleListItemMetrics.default(),
): SimpleListItemStyle = SimpleListItemStyle(colors, metrics)

public fun SimpleListItemStyle.Companion.dark(
    colors: SimpleListItemColors = SimpleListItemColors.dark(),
    metrics: SimpleListItemMetrics = SimpleListItemMetrics.default(),
): SimpleListItemStyle = SimpleListItemStyle(colors, metrics)

@Composable
public fun SimpleListItemStyle.Companion.fullWidth(): SimpleListItemStyle =
    if (JewelTheme.isDark) darkFullWidth() else lightFullWidth()

public fun SimpleListItemStyle.Companion.lightFullWidth(
    colors: SimpleListItemColors = SimpleListItemColors.light(),
    metrics: SimpleListItemMetrics = SimpleListItemMetrics.fullWidth(),
): SimpleListItemStyle = SimpleListItemStyle(colors, metrics)

public fun SimpleListItemStyle.Companion.darkFullWidth(
    colors: SimpleListItemColors = SimpleListItemColors.light(),
    metrics: SimpleListItemMetrics = SimpleListItemMetrics.fullWidth(),
): SimpleListItemStyle = SimpleListItemStyle(colors, metrics)

public fun SimpleListItemColors.Companion.light(
    background: Color = Color.Unspecified,
    backgroundActive: Color = background,
    backgroundSelected: Color = IntUiLightTheme.colors.gray(11),
    backgroundSelectedActive: Color = IntUiLightTheme.colors.blue(11),
    content: Color = Color.Unspecified,
    contentActive: Color = Color.Unspecified,
    contentSelected: Color = Color.Unspecified,
    contentSelectedActive: Color = Color.Unspecified,
): SimpleListItemColors =
    SimpleListItemColors(
        background = background,
        backgroundActive = backgroundActive,
        backgroundSelected = backgroundSelected,
        backgroundSelectedActive = backgroundSelectedActive,
        content = content,
        contentActive = contentActive,
        contentSelected = contentSelected,
        contentSelectedActive = contentSelectedActive,
    )

public fun SimpleListItemColors.Companion.dark(
    background: Color = Color.Unspecified,
    backgroundActive: Color = background,
    backgroundSelected: Color = IntUiLightTheme.colors.gray(2),
    backgroundSelectedActive: Color = IntUiLightTheme.colors.blue(2),
    content: Color = Color.Unspecified,
    contentActive: Color = Color.Unspecified,
    contentSelected: Color = Color.Unspecified,
    contentSelectedActive: Color = Color.Unspecified,
): SimpleListItemColors =
    SimpleListItemColors(
        background = background,
        backgroundActive = backgroundActive,
        backgroundSelected = backgroundSelected,
        backgroundSelectedActive = backgroundSelectedActive,
        content = content,
        contentActive = contentActive,
        contentSelected = contentSelected,
        contentSelectedActive = contentSelectedActive,
    )

public fun SimpleListItemMetrics.Companion.default(
    innerPadding: PaddingValues = PaddingValues(horizontal = 6.dp, vertical = 2.dp),
    outerPadding: PaddingValues = PaddingValues(horizontal = 7.dp, vertical = 1.dp),
    selectionBackgroundCornerSize: CornerSize = CornerSize(4.dp),
    iconTextGap: Dp = 3.dp,
): SimpleListItemMetrics = SimpleListItemMetrics(innerPadding, outerPadding, selectionBackgroundCornerSize, iconTextGap)

public fun SimpleListItemMetrics.Companion.fullWidth(
    innerPadding: PaddingValues = PaddingValues(horizontal = 6.dp, vertical = 2.dp),
    outerPadding: PaddingValues = PaddingValues(),
    selectionBackgroundCornerSize: CornerSize = CornerSize(0.dp),
    iconTextGap: Dp = 3.dp,
): SimpleListItemMetrics = SimpleListItemMetrics(innerPadding, outerPadding, selectionBackgroundCornerSize, iconTextGap)
