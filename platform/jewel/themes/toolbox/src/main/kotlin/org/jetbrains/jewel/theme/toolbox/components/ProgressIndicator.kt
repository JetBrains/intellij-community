package org.jetbrains.jewel.theme.toolbox.components

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.progressSemantics
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.unit.LayoutDirection
import org.jetbrains.jewel.shape
import org.jetbrains.jewel.theme.toolbox.styles.LocalProgressIndicatorStyle
import org.jetbrains.jewel.theme.toolbox.styles.ProgressIndicatorAppearance
import org.jetbrains.jewel.theme.toolbox.styles.ProgressIndicatorStyle

@Composable
fun LinearProgressIndicator(
    value: Float,
    modifier: Modifier = Modifier,
    style: ProgressIndicatorStyle = LocalProgressIndicatorStyle.current,
) {
    val appearance = style.appearance(ProgressIndicatorState.Normal)
    val shapeModifier = if (appearance.shapeStroke != null || appearance.backgroundColor != Color.Unspecified)
        Modifier.shape(appearance.shape, appearance.shapeStroke, appearance.backgroundColor)
    else
        Modifier
    Box(
        modifier.progressSemantics(value).then(shapeModifier)
            .defaultMinSize(appearance.minWidth, appearance.minHeight)
            .drawBehind {
                with(appearance) {
                    painter(0f, value, appearance)
                }
            }
    )
}

@Composable
fun LinearProgressIndicator(
    modifier: Modifier = Modifier,
    style: ProgressIndicatorStyle = LocalProgressIndicatorStyle.current,
) {
    val infiniteTransition = rememberInfiniteTransition()
    val firstLineHead by infiniteTransition.animateFloat(
        0f,
        1f,
        infiniteRepeatable(
            animation = keyframes {
                durationMillis = LinearAnimationDuration
                0f at FirstLineHeadDelay with FirstLineHeadEasing
                1f at FirstLineHeadDuration + FirstLineHeadDelay
            }
        )
    )
    val firstLineTail by infiniteTransition.animateFloat(
        0f,
        1f,
        infiniteRepeatable(
            animation = keyframes {
                durationMillis = LinearAnimationDuration
                0f at FirstLineTailDelay with FirstLineTailEasing
                1f at FirstLineTailDuration + FirstLineTailDelay
            }
        )
    )
    val secondLineHead by infiniteTransition.animateFloat(
        0f,
        1f,
        infiniteRepeatable(
            animation = keyframes {
                durationMillis = LinearAnimationDuration
                0f at SecondLineHeadDelay with SecondLineHeadEasing
                1f at SecondLineHeadDuration + SecondLineHeadDelay
            }
        )
    )
    val secondLineTail by infiniteTransition.animateFloat(
        0f,
        1f,
        infiniteRepeatable(
            animation = keyframes {
                durationMillis = LinearAnimationDuration
                0f at SecondLineTailDelay with SecondLineTailEasing
                1f at SecondLineTailDuration + SecondLineTailDelay
            }
        )
    )

    val appearance = style.appearance(ProgressIndicatorState.Normal)
    val shapeModifier = if (appearance.shapeStroke != null || appearance.backgroundColor != Color.Unspecified)
        Modifier.shape(appearance.shape, appearance.shapeStroke, appearance.backgroundColor)
    else
        Modifier

    Box(
        modifier.then(shapeModifier)
            .defaultMinSize(appearance.minWidth, appearance.minHeight)
            .drawBehind {
                with(appearance) {
                    if (firstLineHead > firstLineTail)
                        painter(firstLineTail, firstLineHead, appearance)
                    if (secondLineHead > secondLineTail)
                        painter(secondLineTail, secondLineHead, appearance)
                }
            }
    )
}

enum class ProgressIndicatorState {
    Normal
}

fun DrawScope.drawRectangleProgress(
    startFraction: Float,
    endFraction: Float,
    appearance: ProgressIndicatorAppearance,
) {
    drawProgress(startFraction, endFraction, appearance.progressPadding) { offset, size ->
        drawRect(appearance.color, offset, size)
    }
}

fun DrawScope.drawRoundedRectangleProgress(
    startFraction: Float,
    endFraction: Float,
    appearance: ProgressIndicatorAppearance,
    cornerSize: CornerSize,
) {
    drawProgress(startFraction, endFraction, appearance.progressPadding) { offset, size ->
        val cornerSizePx = cornerSize.toPx(size, this)
        val cornerRadius = CornerRadius(cornerSizePx, cornerSizePx)
        drawRoundRect(appearance.color, offset, size, cornerRadius = cornerRadius)
    }
}

fun DrawScope.drawProgress(
    startFraction: Float,
    endFraction: Float,
    padding: PaddingValues,
    painter: DrawScope.(Offset, Size) -> Unit
) {
    val startPadding = padding.calculateStartPadding(layoutDirection).toPx()
    val endPadding = padding.calculateEndPadding(layoutDirection).toPx()
    val topPadding = padding.calculateTopPadding().toPx()
    val bottomPadding = padding.calculateBottomPadding().toPx()

    val width = size.width - startPadding - endPadding
    val height = size.height - topPadding - bottomPadding

    val barStart = startFraction * width
    val barEnd = endFraction * width

    if (layoutDirection == LayoutDirection.Ltr) {
        clipRect(
            left = startPadding + barStart,
            top = topPadding,
            right = startPadding + barEnd,
            bottom = topPadding + height
        ) {
            painter(Offset(startPadding, topPadding), Size(width, height))
        }
    } else {
        clipRect(
            left = startPadding + barEnd,
            top = topPadding,
            right = startPadding + barStart,
            bottom = topPadding + height
        ) {
            painter(Offset(startPadding, topPadding), Size(width, height))
        }
    }
}

// Indeterminate linear indicator transition specs
// Total duration for one cycle
private const val LinearAnimationDuration = 1800

// Duration of the head and tail animations for both lines
private const val FirstLineHeadDuration = 750
private const val FirstLineTailDuration = 850
private const val SecondLineHeadDuration = 567
private const val SecondLineTailDuration = 533

// Delay before the start of the head and tail animations for both lines
private const val FirstLineHeadDelay = 0
private const val FirstLineTailDelay = 333
private const val SecondLineHeadDelay = 1000
private const val SecondLineTailDelay = 1267

private val FirstLineHeadEasing = CubicBezierEasing(0.2f, 0f, 0.8f, 1f)
private val FirstLineTailEasing = CubicBezierEasing(0.4f, 0f, 1f, 1f)
private val SecondLineHeadEasing = CubicBezierEasing(0f, 0f, 0.65f, 1f)
private val SecondLineTailEasing = CubicBezierEasing(0.1f, 0f, 0.45f, 1f)
