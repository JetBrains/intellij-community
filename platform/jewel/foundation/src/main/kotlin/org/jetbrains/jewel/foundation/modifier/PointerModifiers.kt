package org.jetbrains.jewel.foundation.modifier

import androidx.compose.ui.Modifier
import androidx.compose.ui.awt.awtEventOrNull
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import java.awt.event.MouseEvent

public fun Modifier.onHover(onHover: (Boolean) -> Unit): Modifier =
    pointerInput(onHover) {
        awaitPointerEventScope {
            while (true) {
                val event = awaitPointerEvent()
                when (event.type) {
                    PointerEventType.Enter -> onHover(true)
                    PointerEventType.Exit -> onHover(false)
                }
            }
        }
    }

public fun Modifier.onMove(onMove: (MouseEvent?) -> Unit): Modifier =
    pointerInput(onMove) {
        awaitPointerEventScope {
            while (true) {
                val event = awaitPointerEvent()
                when (event.type) {
                    PointerEventType.Move -> onMove(event.awtEventOrNull)
                }
            }
        }
    }
