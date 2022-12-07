package org.jetbrains.jewel.themes.expui.standalone.control

import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.progressSemantics
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.unit.dp
import org.jetbrains.jewel.themes.expui.standalone.style.AreaColors
import org.jetbrains.jewel.themes.expui.standalone.style.AreaProvider
import org.jetbrains.jewel.themes.expui.standalone.theme.LightTheme

class ProgressBarColors(
    override val normalAreaColors: AreaColors,
    val indeterminateAreaColors: AreaColors,
) : AreaProvider

val LocalProgressBarColors = compositionLocalOf {
    LightTheme.ProgressBarColors
}

@Composable
fun ProgressBar(
    progress: Float,
    modifier: Modifier = Modifier,
    colors: ProgressBarColors = LocalProgressBarColors.current,
) {
    val currentColors = colors.normalAreaColors
    Canvas(
        modifier.progressSemantics(progress).size(200.dp, 4.dp)
    ) {
        val strokeWidth = size.height
        val length = size.width

        drawLine(
            currentColors.startBackground,
            Offset(0f, strokeWidth / 2f),
            Offset(length, strokeWidth / 2f),
            strokeWidth,
            cap = StrokeCap.Round
        )
        drawLine(
            currentColors.foreground,
            Offset(0f, strokeWidth / 2f),
            Offset(length * progress, strokeWidth / 2f),
            strokeWidth,
            cap = StrokeCap.Round
        )
    }
}

@Composable
fun ProgressBar(
    modifier: Modifier = Modifier,
    colors: ProgressBarColors = LocalProgressBarColors.current,
) {
    val transition = rememberInfiniteTransition()
    val currentOffset by transition.animateFloat(
        0f, 1f,
        infiniteRepeatable(
            animation = keyframes {
                durationMillis = 1000
            }
        )
    )
    val currentColors = colors.indeterminateAreaColors
    Canvas(
        modifier.progressSemantics().size(200.dp, 4.dp)
    ) {
        val strokeWidth = size.height
        val length = size.width
        val offset = currentOffset * length
        val brush = Brush.linearGradient(
            listOf(currentColors.startBackground, currentColors.endBackground, currentColors.startBackground),
            start = Offset(offset, 0f),
            end = Offset(offset + length, 0f),
            tileMode = TileMode.Repeated
        )
        drawLine(
            brush, Offset(0f, strokeWidth / 2f), Offset(length, strokeWidth / 2f), strokeWidth, cap = StrokeCap.Round
        )
    }
}
