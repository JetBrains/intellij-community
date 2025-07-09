// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jewel.ui.component

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.TooltipArea as ComposeTooltipArea
import androidx.compose.foundation.TooltipPlacement
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.input.pointer.PointerEvent
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.areAnyPressed
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.jetbrains.jewel.foundation.ExperimentalJewelApi
import org.jetbrains.jewel.foundation.JewelFlags

/**
 * A composable function that provides a tooltip area to display additional information associated with a UI element.
 * The tooltip appears when the user hovers over the content with their cursor, after an optional delay.
 *
 * This function behavior is influenced by the [JewelFlags.useCustomPopupRenderer]. If set to true, it will use the
 * custom popup implementation. Otherwise, it will use the default Compose tooltip implementation.
 *
 * @param tooltip Composable content of the tooltip.
 * @param modifier The modifier to be applied to the layout.
 * @param delayMillis Delay in milliseconds.
 * @param tooltipPlacement Defines position of the tooltip.
 * @param content Composable content that the current tooltip is set to.
 * @see org.jetbrains.jewel.ui.component.Popup
 * @see androidx.compose.ui.window.Popup
 */
@ExperimentalJewelApi
@Composable
public fun TooltipArea(
    tooltip: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    delayMillis: Int = 500,
    tooltipPlacement: TooltipPlacement = TooltipPlacement.CursorPoint(offset = DpOffset(0.dp, 16.dp)),
    content: @Composable () -> Unit,
) {
    if (JewelFlags.useCustomPopupRenderer) {
        JewelTooltipArea(
            tooltip = tooltip,
            modifier = modifier,
            delayMillis = delayMillis,
            tooltipPlacement = tooltipPlacement,
            content = content,
        )
    } else {
        ComposeTooltipArea(
            tooltip = tooltip,
            modifier = modifier,
            delayMillis = delayMillis,
            tooltipPlacement = tooltipPlacement,
            content = content,
        )
    }
}

/** Copy of the [androidx.compose.foundation.TooltipArea], but using our implementation of [Popup]. */
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
