package org.jetbrains.jewel.foundation.modifier

import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput

public fun Modifier.onHover(onHover: (Boolean) -> Unit): Modifier =
    pointerInput(Unit) {
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
