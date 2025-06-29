// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jewel.ui.component

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.TooltipPlacement
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.input.pointer.*
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.jetbrains.jewel.foundation.ExperimentalJewelApi
import org.jetbrains.jewel.foundation.JewelConfigs

@ExperimentalJewelApi
@Composable
public fun TooltipArea(
    tooltip: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    delayMillis: Int = 500,
    tooltipPlacement: TooltipPlacement = TooltipPlacement.CursorPoint(offset = DpOffset(0.dp, 16.dp)),
    content: @Composable () -> Unit,
) {
    if (JewelConfigs.useCustomPopupRender) {
        JewelTooltipArea(
            tooltip = tooltip,
            modifier = modifier,
            delayMillis = delayMillis,
            tooltipPlacement = tooltipPlacement,
            content = content,
        )
    } else {
        androidx.compose.foundation.TooltipArea(
            tooltip = tooltip,
            modifier = modifier,
            delayMillis = delayMillis,
            tooltipPlacement = tooltipPlacement,
            content = content,
        )
    }
}

@Composable
private fun JewelTooltipArea(
    tooltip: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    delayMillis: Int = 500,
    tooltipPlacement: TooltipPlacement = TooltipPlacement.CursorPoint(offset = DpOffset(0.dp, 16.dp)),
    content: @Composable () -> Unit,
) {
    var parentBounds by remember { mutableStateOf(Rect.Zero) }
    var cursorPosition by remember { mutableStateOf(Offset.Zero) }
    var isVisible by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    var job: Job? by remember { mutableStateOf(null) }

    fun startShowing() {
        if (job?.isActive == true) { // Don't restart the job if it's already active
            return
        }
        job =
            scope.launch {
                delay(delayMillis.toLong())
                isVisible = true
            }
    }

    fun hide() {
        job?.cancel()
        job = null
        isVisible = false
    }

    fun hideIfNotHovered(globalPosition: Offset) {
        if (!parentBounds.contains(globalPosition)) {
            hide()
        }
    }

    Box(
        modifier =
            modifier
                .onGloballyPositioned { parentBounds = it.boundsInWindow() }
                .onPointerEvent(PointerEventType.Enter) {
                    cursorPosition = it.position
                    if (!isVisible && !it.buttons.areAnyPressed) {
                        startShowing()
                    }
                }
                .onPointerEvent(PointerEventType.Move) {
                    cursorPosition = it.position
                    if (!isVisible && !it.buttons.areAnyPressed) {
                        startShowing()
                    }
                }
                .onPointerEvent(PointerEventType.Exit) { hideIfNotHovered(parentBounds.topLeft + it.position) }
                .onPointerEvent(PointerEventType.Press, pass = PointerEventPass.Initial) { hide() }
    ) {
        content()
        if (isVisible) {
            @OptIn(ExperimentalFoundationApi::class)
            Popup(
                popupPositionProvider = tooltipPlacement.positionProvider(cursorPosition),
                onDismissRequest = { isVisible = false },
            ) {
                var popupPosition by remember { mutableStateOf(Offset.Zero) }
                Box(
                    Modifier.onGloballyPositioned { popupPosition = it.positionInWindow() }
                        .onPointerEvent(PointerEventType.Move) { hideIfNotHovered(popupPosition + it.position) }
                        .onPointerEvent(PointerEventType.Exit) { hideIfNotHovered(popupPosition + it.position) }
                ) {
                    tooltip()
                }
            }
        }
    }
}

private val PointerEvent.position
    get() = changes.first().position
