package org.jetbrains.jewel.intui.standalone.styling

import androidx.compose.foundation.shape.CornerSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.jetbrains.jewel.intui.core.theme.IntUiDarkTheme
import org.jetbrains.jewel.intui.core.theme.IntUiLightTheme
import org.jetbrains.jewel.ui.component.styling.HorizontalProgressBarColors
import org.jetbrains.jewel.ui.component.styling.HorizontalProgressBarMetrics
import org.jetbrains.jewel.ui.component.styling.HorizontalProgressBarStyle
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

@Composable
fun HorizontalProgressBarStyle.Companion.light(
    colors: HorizontalProgressBarColors = HorizontalProgressBarColors.light(),
    metrics: HorizontalProgressBarMetrics = HorizontalProgressBarMetrics.defaults(),
    indeterminateCycleDuration: Duration = 800.milliseconds,
) = HorizontalProgressBarStyle(colors, metrics, indeterminateCycleDuration)

@Composable
fun HorizontalProgressBarStyle.Companion.dark(
    colors: HorizontalProgressBarColors = HorizontalProgressBarColors.dark(),
    metrics: HorizontalProgressBarMetrics = HorizontalProgressBarMetrics.defaults(),
    indeterminateCycleDuration: Duration = 800.milliseconds,
) = HorizontalProgressBarStyle(colors, metrics, indeterminateCycleDuration)

@Composable
fun HorizontalProgressBarColors.Companion.light(
    track: Color = IntUiLightTheme.colors.grey(11),
    progress: Color = IntUiLightTheme.colors.blue(4),
    indeterminateBase: Color = IntUiLightTheme.colors.blue(9),
    indeterminateHighlight: Color = IntUiLightTheme.colors.blue(4),
) = HorizontalProgressBarColors(
    track,
    progress,
    indeterminateBase,
    indeterminateHighlight,
)

@Composable
fun HorizontalProgressBarColors.Companion.dark(
    track: Color = IntUiDarkTheme.colors.grey(4),
    progress: Color = IntUiDarkTheme.colors.blue(7),
    indeterminateBase: Color = IntUiDarkTheme.colors.blue(9),
    indeterminateHighlight: Color = IntUiDarkTheme.colors.blue(5),
) = HorizontalProgressBarColors(
    track,
    progress,
    indeterminateBase,
    indeterminateHighlight,
)

fun HorizontalProgressBarMetrics.Companion.defaults(
    cornerSize: CornerSize = CornerSize(100),
    minHeight: Dp = 4.dp,
    indeterminateHighlightWidth: Dp = 140.dp,
) = HorizontalProgressBarMetrics(
    cornerSize,
    minHeight,
    indeterminateHighlightWidth,
)
