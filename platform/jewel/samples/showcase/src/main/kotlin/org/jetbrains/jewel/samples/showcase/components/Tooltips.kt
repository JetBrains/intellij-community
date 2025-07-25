// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the
// Apache 2.0 license.
package org.jetbrains.jewel.samples.showcase.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.delay
import org.jetbrains.jewel.ui.component.CheckboxRow
import org.jetbrains.jewel.ui.component.DefaultButton
import org.jetbrains.jewel.ui.component.Text
import org.jetbrains.jewel.ui.component.Tooltip
import org.jetbrains.jewel.ui.component.styling.LocalTooltipStyle
import org.jetbrains.jewel.ui.component.styling.TooltipAutoHideBehavior
import org.jetbrains.jewel.ui.component.styling.TooltipStyle

@Composable
public fun Tooltips(modifier: Modifier = Modifier) {
    var toggleEnabled by remember { mutableStateOf(true) }
    var enabled by remember { mutableStateOf(true) }
    var neverHide by remember { mutableStateOf(false) }

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

    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        Tooltip(tooltip = { Text("This is a tooltip") }, enabled = enabled, style = tooltipStyle) {
            // Any content works — this is a button just because it's focusable
            DefaultButton({}) { Text("Hover me!") }
        }

        CheckboxRow("Enabled", enabled, { enabled = it })

        CheckboxRow("Toggle enabled every 1s", toggleEnabled, { toggleEnabled = it })

        CheckboxRow("Never hide", neverHide, { neverHide = it })
    }
}
