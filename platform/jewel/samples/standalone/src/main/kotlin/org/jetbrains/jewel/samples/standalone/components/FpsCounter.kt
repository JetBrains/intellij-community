// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jewel.samples.standalone.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import kotlin.math.roundToInt
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.jetbrains.jewel.ui.component.Text

@Composable
internal fun FpsCounter(modifier: Modifier = Modifier, dispatcher: CoroutineDispatcher = Dispatchers.Default) {
    var displayedFPS by remember { mutableIntStateOf(0) }
    var fpsCountMethod by remember { mutableStateOf(FPSCountMethod.RealTime) }
    var minFps by remember { mutableIntStateOf(240) }
    var maxFps by remember { mutableIntStateOf(0) }

    val textContent by remember {
        derivedStateOf {
            when (fpsCountMethod) {
                FPSCountMethod.RealTime -> {
                    "FPS(Realtime):$displayedFPS"
                }

                FPSCountMethod.FixedInterval -> {
                    "FPS(last ${FPS_UPDATE_DELAY}ms):$displayedFPS"
                }

                FPSCountMethod.FixedFrameCount -> {
                    "FPS(last ${FRAME_COUNT} frames):$displayedFPS"
                }
            }
        }
    }

    val minMaxContent by remember { derivedStateOf { "min:$minFps, max:$maxFps" } }
    val textColor by remember { derivedStateOf { displayedFPS.fpsCountColor } }

    Row(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = textContent,
            modifier =
                Modifier.clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
                    fpsCountMethod =
                        when (fpsCountMethod) {
                            FPSCountMethod.FixedInterval -> FPSCountMethod.FixedFrameCount
                            FPSCountMethod.FixedFrameCount -> FPSCountMethod.RealTime
                            FPSCountMethod.RealTime -> FPSCountMethod.FixedInterval
                        }
                    minFps = 240
                    maxFps = 0
                },
            color = textColor,
        )

        Text(
            text = minMaxContent,
            modifier =
                Modifier.clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
                    minFps = 240
                    maxFps = 0
                },
        )
    }

    LaunchedEffect(Unit) {
        val fpsArray = FloatArray(FRAME_COUNT) { 0f }
        val fpsCount = AtomicInteger(0)
        val writeIndex = AtomicInteger(0)
        val lastWriteIndex = AtomicInteger(0)
        val lastUpdTime = AtomicLong(withFrameMillis { it })

        launch(dispatcher) {
            while (true) {
                delay(FPS_UPDATE_DELAY)

                displayedFPS =
                    when (fpsCountMethod) {
                        FPSCountMethod.FixedInterval -> fpsCount.getAndSet(0) * 1000 / FPS_UPDATE_DELAY.toInt()
                        FPSCountMethod.FixedFrameCount -> fpsArray.average().roundToInt()
                        FPSCountMethod.RealTime -> fpsArray[lastWriteIndex.get()].roundToInt()
                    }
                if (displayedFPS > 0) {
                    minFps = minOf(minFps, displayedFPS)
                }
                maxFps = maxOf(maxFps, displayedFPS)
            }
        }

        while (true) {
            withFrameMillis { frameTimeMillis ->
                fpsCount.getAndUpdate { it + 1 }

                fpsArray[writeIndex.get()] = 1000f / (frameTimeMillis - lastUpdTime.getAndSet(frameTimeMillis))

                lastWriteIndex.set(writeIndex.getAndUpdate { (it + 1) % fpsArray.size })
            }
        }
    }
}

private const val FPS_UPDATE_DELAY = 250L
private const val FRAME_COUNT = 20

private val Int.fpsCountColor: Color
    get() =
        when {
            this > 55 -> Color.Green
            this > 45 -> Color.Yellow
            else -> Color.Red
        }

internal enum class FPSCountMethod {
    FixedInterval,
    FixedFrameCount,
    RealTime,
}
