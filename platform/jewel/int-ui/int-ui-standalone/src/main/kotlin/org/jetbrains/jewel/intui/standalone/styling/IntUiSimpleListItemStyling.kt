package org.jetbrains.jewel.intui.standalone.styling

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.intui.core.theme.IntUiDarkTheme
import org.jetbrains.jewel.intui.core.theme.IntUiLightTheme
import org.jetbrains.jewel.ui.component.styling.SimpleListItemColors
import org.jetbrains.jewel.ui.component.styling.SimpleListItemMetrics
import org.jetbrains.jewel.ui.component.styling.SimpleListItemStyle

/** Creates an Int UI default [SimpleListItemStyle], choosing between light and dark based on the current theme. */
@Composable
public fun SimpleListItemStyle.Companion.default(): SimpleListItemStyle = if (JewelTheme.isDark) dark() else light()

/** Creates an Int UI light [SimpleListItemStyle] with the provided parameters. */
public fun SimpleListItemStyle.Companion.light(
    colors: SimpleListItemColors = SimpleListItemColors.light(),
    metrics: SimpleListItemMetrics = SimpleListItemMetrics.default(),
): SimpleListItemStyle = SimpleListItemStyle(colors, metrics)

/** Creates an Int UI dark [SimpleListItemStyle] with the provided parameters. */
public fun SimpleListItemStyle.Companion.dark(
    colors: SimpleListItemColors = SimpleListItemColors.dark(),
    metrics: SimpleListItemMetrics = SimpleListItemMetrics.default(),
): SimpleListItemStyle = SimpleListItemStyle(colors, metrics)

/**
 * Creates an Int UI full-width default [SimpleListItemStyle], choosing between light and dark based on the current
 * theme.
 */
@Composable
public fun SimpleListItemStyle.Companion.fullWidth(): SimpleListItemStyle =
    if (JewelTheme.isDark) darkFullWidth() else lightFullWidth()

/** Creates an Int UI light full-width [SimpleListItemStyle] with the provided parameters. */
public fun SimpleListItemStyle.Companion.lightFullWidth(
    colors: SimpleListItemColors = SimpleListItemColors.light(),
    metrics: SimpleListItemMetrics = SimpleListItemMetrics.fullWidth(),
): SimpleListItemStyle = SimpleListItemStyle(colors, metrics)

/** Creates an Int UI dark full-width [SimpleListItemStyle] with the provided parameters. */
public fun SimpleListItemStyle.Companion.darkFullWidth(
    colors: SimpleListItemColors = SimpleListItemColors.dark(),
    metrics: SimpleListItemMetrics = SimpleListItemMetrics.fullWidth(),
): SimpleListItemStyle = SimpleListItemStyle(colors, metrics)

/** Creates an Int UI light [SimpleListItemColors] with the provided parameters. */
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

/** Creates an Int UI dark [SimpleListItemColors] with the provided parameters. */
public fun SimpleListItemColors.Companion.dark(
    background: Color = Color.Unspecified,
    backgroundActive: Color = background,
    backgroundSelected: Color = IntUiDarkTheme.colors.gray(2),
    backgroundSelectedActive: Color = IntUiDarkTheme.colors.blue(2),
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

/** Creates an Int UI default [SimpleListItemMetrics] with the provided parameters. */
public fun SimpleListItemMetrics.Companion.default(
    innerPadding: PaddingValues = PaddingValues(horizontal = 6.dp, vertical = 2.dp),
    outerPadding: PaddingValues = PaddingValues(horizontal = 7.dp, vertical = 1.dp),
    selectionBackgroundCornerSize: CornerSize = CornerSize(4.dp),
    iconTextGap: Dp = 3.dp,
): SimpleListItemMetrics = SimpleListItemMetrics(innerPadding, outerPadding, selectionBackgroundCornerSize, iconTextGap)

/** Creates an Int UI full-width [SimpleListItemMetrics] with the provided parameters. */
public fun SimpleListItemMetrics.Companion.fullWidth(
    innerPadding: PaddingValues = PaddingValues(horizontal = 6.dp, vertical = 2.dp),
    outerPadding: PaddingValues = PaddingValues(),
    selectionBackgroundCornerSize: CornerSize = CornerSize(0.dp),
    iconTextGap: Dp = 3.dp,
): SimpleListItemMetrics = SimpleListItemMetrics(innerPadding, outerPadding, selectionBackgroundCornerSize, iconTextGap)
