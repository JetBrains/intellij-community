package org.jetbrains.jewel.components

import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.unit.dp

/**
 * Place-holder CircularProgressIndicator.
 */
@Suppress("MagicNumber") // TODO replace with a real one
@Composable
fun CircularProgressIndicator(
    modifier: Modifier = Modifier,
    color: Color = Color.LightGray
) {
    val pathEffect = PathEffect.dashPathEffect(floatArrayOf(3f, 3f), 0f)
    val transition = rememberInfiniteTransition()
    val rotation = transition.animateFloat(
        0f,
        360f,
        infiniteRepeatable(
            animation = keyframes {
                durationMillis = 4000
                0f at 0
                360f at 4000
            }
        )
    )
    Canvas(
        modifier
            .size(20.dp)
            .focusable()
    ) {
        rotate(rotation.value) {
            drawCircle(
                brush = SolidColor(color),
                style = Stroke(
                    4f,
                    cap = StrokeCap.Butt,
                    pathEffect = pathEffect
                ),
                radius = 8.dp.value
            )
        }
    }
}
