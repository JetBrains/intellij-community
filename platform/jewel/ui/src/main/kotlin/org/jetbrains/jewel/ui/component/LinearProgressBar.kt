package org.jetbrains.jewel.ui.component

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.component.styling.HorizontalProgressBarStyle
import org.jetbrains.jewel.ui.theme.horizontalProgressBarStyle

/**
 * A horizontal progress bar component that follows the standard visual styling.
 *
 * Provides a progress indicator that fills from left to right (or right to left in RTL layouts) to show the progress of
 * an operation. The progress is represented as a filled portion of the bar, with customizable colors and styling.
 *
 * **Guidelines:** [on IJP SDK webhelp](https://plugins.jetbrains.com/docs/intellij/progress-bar.html)
 *
 * **Usage example:**
 * [`ProgressBar.kt`](https://github.com/JetBrains/intellij-community/blob/master/platform/jewel/samples/showcase/src/main/kotlin/org/jetbrains/jewel/samples/showcase/components/ProgressBar.kt)
 *
 * **Swing equivalent:** [`JProgressBar`](https://docs.oracle.com/javase/tutorial/uiswing/components/progress.html)
 *
 * @param progress The current progress value between 0 and 1
 * @param modifier Modifier to be applied to the progress bar
 * @param style The visual styling configuration for the progress bar
 * @see javax.swing.JProgressBar
 */
@Composable
public fun HorizontalProgressBar(
    progress: Float, // from 0 to 1
    modifier: Modifier = Modifier,
    style: HorizontalProgressBarStyle = JewelTheme.horizontalProgressBarStyle,
) {
    val colors = style.colors
    val shape = RoundedCornerShape(style.metrics.cornerSize)

    Box(
        modifier.defaultMinSize(minHeight = style.metrics.minHeight).clip(shape).drawWithContent {
            drawRect(color = colors.track) // Draw the background
            val progressWidth = size.width * progress
            val progressX = if (layoutDirection == LayoutDirection.Ltr) 0f else size.width - progressWidth

            val cornerSizePx = style.metrics.cornerSize.toPx(size, Density(density, fontScale))
            val cornerRadius = CornerRadius(cornerSizePx, cornerSizePx)
            drawRoundRect(
                color = colors.progress,
                topLeft = Offset(progressX, 0f),
                size = size.copy(width = progressWidth),
                cornerRadius = cornerRadius,
            )
        }
    )
}

/**
 * An indeterminate horizontal progress bar component that follows the standard visual styling.
 *
 * Provides an animated progress indicator for operations where the progress cannot be determined. Displays a moving
 * highlight that animates continuously to indicate ongoing activity.
 *
 * **Guidelines:** [on IJP SDK webhelp](https://plugins.jetbrains.com/docs/intellij/progress-bar.html)
 *
 * **Usage example:**
 * [`ProgressBar.kt`](https://github.com/JetBrains/intellij-community/blob/master/platform/jewel/samples/showcase/src/main/kotlin/org/jetbrains/jewel/samples/showcase/components/ProgressBar.kt)
 *
 * **Swing equivalent:** [`JProgressBar`](https://docs.oracle.com/javase/tutorial/uiswing/components/progress.html) with
 * [setIndeterminate(true)](https://docs.oracle.com/javase/8/docs/api/javax/swing/JProgressBar.html#setIndeterminate-boolean-)
 *
 * @param modifier Modifier to be applied to the progress bar
 * @param style The visual styling configuration for the progress bar
 * @see javax.swing.JProgressBar
 */
@Composable
public fun IndeterminateHorizontalProgressBar(
    modifier: Modifier = Modifier,
    style: HorizontalProgressBarStyle = JewelTheme.horizontalProgressBarStyle,
) {
    val infiniteTransition = rememberInfiniteTransition()

    val cycleDurationMillis by remember { mutableStateOf(style.indeterminateCycleDuration.inWholeMilliseconds.toInt()) }
    val animatedProgress by
        infiniteTransition.animateFloat(
            initialValue = 0f,
            targetValue = 2f,
            animationSpec =
                infiniteRepeatable(
                    tween(durationMillis = cycleDurationMillis, easing = LinearEasing),
                    repeatMode = RepeatMode.Restart,
                ),
        )

    val highlightWidth = style.metrics.indeterminateHighlightWidth
    val colors = style.colors
    val colorsList by remember { mutableStateOf(listOf(colors.indeterminateBase, colors.indeterminateHighlight)) }
    val shape = RoundedCornerShape(style.metrics.cornerSize)

    Box(
        modifier.defaultMinSize(minHeight = style.metrics.minHeight).clip(shape).drawWithContent {
            drawRect(color = colors.track) // Draw the background
            val x = highlightWidth.value * animatedProgress

            // Draw the animated highlight
            drawRect(
                Brush.linearGradient(
                    colors = colorsList,
                    start = Offset(x, 0f),
                    end = Offset(x + highlightWidth.value, 0f),
                    TileMode.Mirror,
                )
            )
        }
    )
}
