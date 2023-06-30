package org.jetbrains.jewel.samples.standalone.components

import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import org.jetbrains.jewel.AnimatedDefiniteLinearProgressBar
import org.jetbrains.jewel.IndeterminateLinearProgressBar
import org.jetbrains.jewel.LinearProgressBar
import org.jetbrains.jewel.Text

@Composable
fun ProgressBar() {
    val transition = rememberInfiniteTransition()
    val currentOffset by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = keyframes {
                durationMillis = 4000
                0f at 1000
                1f at 3000
            }
        )
    )
    var intermittentProgression by remember { mutableStateOf(0f) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(800)
            if (intermittentProgression >= .9) {
                intermittentProgression = 0f
            } else {
                intermittentProgression += .25f
            }
        }
    }
    Column {
        Text("LinearProgressBar - linear progression")
        Row(Modifier.width(600.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            LinearProgressBar(Modifier.width(500.dp), currentOffset)
            Text("${(currentOffset * 100).toInt()} %")
        }
    }
    Column {
        Text("LinearProgressBar - non linear progression ")
        Row(Modifier.width(600.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            LinearProgressBar(Modifier.width(500.dp), intermittentProgression)
            Text("${(intermittentProgression * 100).toInt()} %")
        }
    }
    Column {
        Text("AnimatedProgressBar - non linear progression")
        Row(Modifier.width(600.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            AnimatedDefiniteLinearProgressBar(Modifier.width(500.dp), intermittentProgression)
            Text("${(intermittentProgression * 100).toInt()} %")
        }
    }
    Column {
        Text("IndeterminateProgressBar")
        Row(Modifier.width(600.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            IndeterminateLinearProgressBar(Modifier.width(500.dp))
            Text("----")
        }
    }
}
