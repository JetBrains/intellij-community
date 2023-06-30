package org.jetbrains.jewel

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.unit.Dp

@Composable
fun AnimatedDefiniteLinearProgressBar(
    modifier: Modifier = Modifier,
    targetProgress: Float, // from 0 to 1
    defaults: ProgressBarDefaults = IntelliJTheme.progressBarDefaults,
    colors: ProgressBarColors = defaults.colors()
) {
    val progress by animateFloatAsState(
        targetValue = targetProgress,
        animationSpec = tween(durationMillis = 250)
    )
    val trackColor by colors.trackColor()
    val progressColor by colors.determinateProgressColor()
    val colorsList = listOf(progressColor)

    LinearProgressBarImpl(
        modifier,
        progress,
        defaults,
        trackColor,
        colorsList
    )
}

@Composable
fun LinearProgressBar(
    modifier: Modifier = Modifier,
    progress: Float, // from 0 to 1
    defaults: ProgressBarDefaults = IntelliJTheme.progressBarDefaults,
    colors: ProgressBarColors = defaults.colors()
) {
    val trackColor by colors.trackColor()
    val progressColor by colors.determinateProgressColor()
    LinearProgressBarImpl(modifier, progress, defaults, trackColor, listOf(progressColor))
}

@Composable
internal fun LinearProgressBarImpl(
    modifier: Modifier = Modifier,
    progress: Float, // from 0 to 1
    defaults: ProgressBarDefaults,
    trackColor: Color,
    brushColorList: List<Color>
) {
    val shape = defaults.clipShape()
    val height = defaults.height()
    Box(
        modifier
            .height(height)
            .clip(shape)
            .drawWithContent {
                drawRect(color = trackColor) // Draw the background
                val progressWidth = size.width * progress
                val progressOutline = shape.createOutline(size.copy(progressWidth), layoutDirection, this)
                val brush = if (brushColorList.size > 1) {
                    val x = size.width * progress
                    Brush.horizontalGradient(
                        colors = brushColorList,
                        startX = 0f,
                        endX = x,
                        tileMode = TileMode.Clamp
                    )
                } else {
                    SolidColor(brushColorList.first())
                }
                drawOutline(progressOutline, brush)
            }
    )
}

@Composable
fun IndeterminateLinearProgressBar(
    modifier: Modifier = Modifier,
    defaults: ProgressBarDefaults = IntelliJTheme.progressBarDefaults,
    colors: ProgressBarColors = defaults.colors()
) {
    val infiniteTransition = rememberInfiniteTransition()

    val animatedProgress by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 2f,
        animationSpec = infiniteRepeatable(
            tween(durationMillis = defaults.animationDurationMillis(), easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        )
    )

    val gradientWidth = defaults.gradientWidth()
    val bgColor by colors.trackColor()
    val startColor by colors.indeterminateStartColor()
    val endColor by colors.indeterminateEndColor()
    val colorsList = listOf(startColor, endColor)
    Box(
        modifier
            .height(defaults.height())
            .clip(defaults.clipShape())
            .drawWithContent {
                drawRect(color = bgColor) // Draw the background
                val x = gradientWidth.value * animatedProgress
                // Define the gradient colors
                val gradient = Brush.linearGradient(
                    colors = colorsList,
                    start = Offset(x, 0f),
                    end = Offset(gradientWidth.value + x, 0f),
                    TileMode.Mirror
                )

                // Draw the animated gradient
                drawRect(brush = gradient)
            }
    )
}

@Stable
interface ProgressBarDefaults {

    @Composable
    fun height(): Dp

    @Composable
    fun clipShape(): RoundedCornerShape

    @Composable
    fun gradientWidth(): Dp

    @Composable
    fun animationDurationMillis(): Int

    @Composable
    fun colors(): ProgressBarColors
}

interface ProgressBarColors {

    @Composable
    fun indeterminateStartColor(): State<Color>

    @Composable
    fun indeterminateEndColor(): State<Color>

    @Composable
    fun determinateProgressColor(): State<Color>

    @Composable
    fun trackColor(): State<Color>
}

fun progressBarDefaultsColors(
    indeterminateStartColor: Color,
    indeterminateEndColor: Color,
    determinateProgressColor: Color,
    trackColor: Color
): ProgressBarColors =
    DefaultProgressBarColors(
        indeterminateStartColor,
        indeterminateEndColor,
        determinateProgressColor,
        trackColor
    )

@Immutable
private class DefaultProgressBarColors(
    private val indeterminateStartColor: Color,
    private val indeterminateEndColor: Color,
    private val progressColor: Color,
    private val trackColor: Color
) : ProgressBarColors {

    @Composable
    override fun indeterminateStartColor(): State<Color> = rememberUpdatedState(indeterminateStartColor)

    @Composable
    override fun indeterminateEndColor(): State<Color> = rememberUpdatedState(indeterminateEndColor)

    @Composable
    override fun determinateProgressColor(): State<Color> = rememberUpdatedState(progressColor)

    @Composable
    override fun trackColor(): State<Color> = rememberUpdatedState(trackColor)
}

internal val LocalProgressBarDefaults =
    staticCompositionLocalOf<ProgressBarDefaults> {
        error("No ProgressBarDefaults provided")
    }
