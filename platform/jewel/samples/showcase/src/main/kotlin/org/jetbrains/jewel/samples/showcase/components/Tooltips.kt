// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the
// Apache 2.0 license.
package org.jetbrains.jewel.samples.showcase.components

import androidx.compose.foundation.TooltipPlacement
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.delay
import org.jetbrains.jewel.ui.Orientation
import org.jetbrains.jewel.ui.component.CheckboxRow
import org.jetbrains.jewel.ui.component.DefaultButton
import org.jetbrains.jewel.ui.component.Divider
import org.jetbrains.jewel.ui.component.FixedCursorPoint
import org.jetbrains.jewel.ui.component.Text
import org.jetbrains.jewel.ui.component.Tooltip
import org.jetbrains.jewel.ui.component.styling.LocalTooltipStyle
import org.jetbrains.jewel.ui.component.styling.TooltipAutoHideBehavior
import org.jetbrains.jewel.ui.component.styling.TooltipStyle

@Composable
public fun Tooltips(modifier: Modifier = Modifier) {
    var enabled by remember { mutableStateOf(true) }
    var toggleEnabled by remember { mutableStateOf(false) }
    var neverHide by remember { mutableStateOf(false) }
    var randomPlacement by remember { mutableStateOf(false) }
    var reshuffle by remember { mutableStateOf(false) }

    var text by remember { mutableStateOf("This is a tooltip!") }
    var placement by remember { mutableStateOf<TooltipPlacement>(FixedCursorPoint(DpOffset(4.dp, 24.dp))) }

    val originalStyle = LocalTooltipStyle.current
    val tooltipStyle by
        remember(originalStyle) {
            derivedStateOf {
                TooltipStyle(
                    colors = originalStyle.colors,
                    metrics = originalStyle.metrics,
                    autoHideBehavior = if (neverHide) TooltipAutoHideBehavior.Never else TooltipAutoHideBehavior.Normal,
                )
            }
        }

    LaunchedEffect(toggleEnabled) {
        if (!toggleEnabled) return@LaunchedEffect

        while (true) {
            delay(1.seconds)
            enabled = !enabled
        }
    }

    LaunchedEffect(randomPlacement) {
        if (!randomPlacement) {
            placement = tooltipStyle.metrics.placement
            return@LaunchedEffect
        }

        while (true) {
            delay(1.seconds)
            placement = FixedCursorPoint(DpOffset((0..50).random().dp, (0..50).random().dp))
        }
    }

    LaunchedEffect(reshuffle) {
        if (!reshuffle) {
            text = "This is a tooltip!"
            return@LaunchedEffect
        }

        while (true) {
            delay(1.seconds)
            text = listOf("This", "is", "a", "tooltip!").shuffled().joinToString(" ")
        }
    }

    Column(modifier, verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Tooltip(tooltip = { Text(text) }, enabled = enabled, style = tooltipStyle, tooltipPlacement = placement) {
            // Any content works — this is a button just because it's focusable
            DefaultButton({}) { Text("Hover me!") }
        }

        Divider(Orientation.Horizontal, Modifier.fillMaxWidth())

        CheckboxRow("Enabled", enabled, { enabled = it })

        CheckboxRow("Toggle enabled every 1s", toggleEnabled, { toggleEnabled = it })

        CheckboxRow("Random Placement every 1s", randomPlacement, { randomPlacement = it })

        CheckboxRow("Shuffle Content every 1s", reshuffle, { reshuffle = it })

        CheckboxRow("Never hide", neverHide, { neverHide = it })
    }
}
