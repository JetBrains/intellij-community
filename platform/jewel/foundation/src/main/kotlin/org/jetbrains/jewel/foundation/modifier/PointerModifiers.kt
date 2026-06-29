package org.jetbrains.jewel.foundation.modifier

import androidx.compose.ui.Modifier
import androidx.compose.ui.awt.awtEventOrNull
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import java.awt.event.MouseEvent

/**
 * Invokes [onHover] with `true` when the pointer enters this composable and `false` when it exits.
 *
 * @param onHover Callback receiving `true` on enter and `false` on exit.
 */
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

/**
 * Invokes [onMove] with the underlying [MouseEvent] (or `null` if unavailable) whenever the pointer moves over this
 * composable.
 *
 * @param onMove Callback receiving the AWT [MouseEvent] on each pointer move event.
 */
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
