package org.jetbrains.jewel.ui.component

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableIntState
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.takeOrElse
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.time.Duration
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.foundation.util.JewelLogger
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
    val frameIndex = rememberSpinnerFrameIndex(style.frameTime)

    Canvas(modifier = modifier.size(iconSize)) {
        // Reading the frame index inside the draw lambda, rather than in the composable scope, keeps invalidations
        // scoped to the draw phase. Since the index only changes once per animation frame, we also avoid redrawing
        // on every display frame, which is what an animated float value would have caused.
        val snappedRotation = frameIndex.intValue * degreesPerSegment

        val diameter = size.minDimension
        val rectWidth = diameter * 2f / ICON_VIEW_BOX_SIZE
        val rectHeight = diameter * 4f / ICON_VIEW_BOX_SIZE
        val cornerRadius = CornerRadius(diameter / ICON_VIEW_BOX_SIZE)
        val segmentTopLeft = Offset(x = center.x - rectWidth / 2f, y = diameter / ICON_VIEW_BOX_SIZE)
        val segmentSize = Size(rectWidth, rectHeight)

        rotate(degrees = snappedRotation, pivot = center) {
            for (i in spinnerSegmentOpacities.indices) {
                val alpha = spinnerSegmentOpacities[i]

                // Fully transparent segments still cost time to draw, but are invisible: skip them
                if (alpha == 0f) continue

                rotate(degrees = -i * degreesPerSegment, pivot = center) {
                    drawRoundRect(
                        color = color,
                        topLeft = segmentTopLeft,
                        size = segmentSize,
                        cornerRadius = cornerRadius,
                        alpha = alpha,
                    )
                }
            }
        }
    }
}

/**
 * Drives the spinner animation by ticking an integer frame index once every [frameTime].
 *
 * Compared to running a float-valued animation and quantising it at draw time, this only invalidates when the value
 * actually changes, instead of on every display frame.
 */
@Composable
private fun rememberSpinnerFrameIndex(frameTime: Duration): MutableIntState {
    val frameIndex = remember { mutableIntStateOf(0) }

    LaunchedEffect(frameTime) {
        // A non-positive frame time would turn the loop below into a busy loop; show a static spinner instead.
        if (frameTime <= Duration.ZERO) {
            JewelLogger.getInstance("CircularProgressIndicator")
                .warn(
                    "Non-positive frameTime received. Indicator will be static until a positive duration is provided."
                )
            return@LaunchedEffect
        }

        while (isActive) {
            delay(frameTime)
            frameIndex.intValue = (frameIndex.intValue + 1) % spinnerSegmentOpacities.size
        }
    }

    return frameIndex
}

private const val FULL_ROTATION_DEGREES = 360f
private const val ICON_VIEW_BOX_SIZE = 16f

private val spinnerSegmentOpacities = floatArrayOf(1f, 0.93f, 0.78f, 0.69f, 0.62f, 0.48f, 0.38f, 0f)
private val degreesPerSegment = FULL_ROTATION_DEGREES / spinnerSegmentOpacities.size
