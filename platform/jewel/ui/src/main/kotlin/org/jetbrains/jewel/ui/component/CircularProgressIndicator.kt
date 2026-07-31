package org.jetbrains.jewel.ui.component

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.takeOrElse
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.component.styling.CircularProgressStyle
import org.jetbrains.jewel.ui.theme.circularProgressStyle

/**
 * Renders a small (16x16dp) animated circular progress indicator that spins indefinitely, indicating an ongoing
 * operation with no known completion time.
 */
@Composable
public fun CircularProgressIndicator(
    modifier: Modifier = Modifier,
    style: CircularProgressStyle = JewelTheme.circularProgressStyle,
    loadingDispatcher: CoroutineDispatcher = Dispatchers.Default,
) {
    CircularProgressIndicatorImpl(
        modifier = modifier,
        iconSize = 16.dp,
        style = style,
        loadingDispatcher = loadingDispatcher,
    )
}

/**
 * Renders a large (32x32dp) animated circular progress indicator that spins indefinitely, indicating an ongoing
 * operation with no known completion time.
 */
@Composable
public fun CircularProgressIndicatorBig(
    modifier: Modifier = Modifier,
    style: CircularProgressStyle = JewelTheme.circularProgressStyle,
    loadingDispatcher: CoroutineDispatcher = Dispatchers.Default,
) {
    CircularProgressIndicatorImpl(
        modifier = modifier,
        iconSize = 32.dp,
        style = style,
        loadingDispatcher = loadingDispatcher,
    )
}

@Suppress("UNUSED_PARAMETER")
@Composable
private fun CircularProgressIndicatorImpl(
    iconSize: Dp,
    style: CircularProgressStyle,
    loadingDispatcher: CoroutineDispatcher,
    modifier: Modifier = Modifier,
) {
    val defaultColor = if (JewelTheme.isDark) Color(0xFF6F737A) else Color(0xFFA8ADBD)
    val color = style.color.takeOrElse { defaultColor }
    val framesCount = spinnerSegmentOpacities.size
    val frameTimeMillis = style.frameTime.inWholeMilliseconds.toInt()

    val transition = rememberInfiniteTransition("CircularProgressIndicator")
    val rotationRatio by
        transition.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec =
                infiniteRepeatable(
                    animation = tween(easing = LinearEasing, durationMillis = frameTimeMillis * framesCount),
                    repeatMode = RepeatMode.Restart,
                ),
        )

    Canvas(modifier = modifier.size(iconSize)) {
        val frameIndex = (rotationRatio * framesCount).toInt() % framesCount
        val snappedRotation = frameIndex * FULL_ROTATION_DEGREES / framesCount

        val diameter = size.minDimension
        val rectWidth = diameter * 2f / ICON_VIEW_BOX_SIZE
        val rectHeight = diameter * 4f / ICON_VIEW_BOX_SIZE
        val cornerRadius = CornerRadius(diameter / ICON_VIEW_BOX_SIZE)
        val segmentTopLeft = Offset(x = center.x - rectWidth / 2f, y = diameter / ICON_VIEW_BOX_SIZE)
        val segmentSize = Size(rectWidth, rectHeight)

        rotate(degrees = snappedRotation, pivot = center) {
            for (i in 0 until framesCount) {
                rotate(degrees = -i * FULL_ROTATION_DEGREES / framesCount, pivot = center) {
                    drawRoundRect(
                        color = color,
                        topLeft = segmentTopLeft,
                        size = segmentSize,
                        cornerRadius = cornerRadius,
                        alpha = spinnerSegmentOpacities[i],
                    )
                }
            }
        }
    }
}

private const val FULL_ROTATION_DEGREES = 360f
private const val ICON_VIEW_BOX_SIZE = 16f

private val spinnerSegmentOpacities = listOf(1f, 0.93f, 0.78f, 0.69f, 0.62f, 0.48f, 0.38f, 0f)
