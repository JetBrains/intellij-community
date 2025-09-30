// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jewel.samples.showcase.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.center
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.center
import androidx.compose.ui.unit.dp
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.focusOutline
import org.jetbrains.jewel.ui.theme.colorPalette
import org.jetbrains.jewel.ui.theme.textFieldStyle

@Composable
internal fun Rotary(value: Double, onValueChange: (Double) -> Unit, modifier: Modifier = Modifier) {
    var focused by remember { mutableStateOf(false) }

    val borderColor =
        if (focused) {
            JewelTheme.textFieldStyle.colors.borderFocused
        } else {
            JewelTheme.textFieldStyle.colors.border
        }

    val palette = JewelTheme.colorPalette
    val mainColor =
        if (JewelTheme.isDark) {
            palette.grayOrNull(6) ?: Color.LightGray
        } else {
            palette.grayOrNull(3) ?: Color.DarkGray
        }

    val requester = remember { FocusRequester() }

    Canvas(
        modifier =
            modifier
                .defaultMinSize(48.dp)
                .focusRequester(requester)
                .onFocusChanged { focused = it.isFocused }
                .focusable()
                .pointerInput(Unit) {
                    detectDragGestures { change, _ ->
                        requester.requestFocus()

                        val center = Offset(size.center.x.toFloat(), size.center.y.toFloat())
                        val dragOffset = change.position - center
                        val angle =
                            (atan2(dragOffset.y, dragOffset.x) * (180f / Math.PI).toFloat() + 90f).let {
                                if (it < 0) it + 360 else it
                            }
                        onValueChange(angle.toDouble())
                    }
                }
                .focusOutline(showOutline = focused, CircleShape)
    ) {
        val radius = size.minDimension / 2f
        val center = size.center

        drawCircle(color = borderColor, radius = radius, center = center, style = Stroke(width = 1.dp.toPx()))

        val lineEndX = center.x + radius * cos(Math.toRadians(value - 90)).toFloat()
        val lineEndY = center.y + radius * sin(Math.toRadians(value - 90)).toFloat()

        drawLine(color = mainColor, start = center, end = Offset(lineEndX, lineEndY), strokeWidth = 2.dp.toPx())

        drawCircle(color = mainColor, radius = 3.dp.toPx(), center = center)
    }
}
