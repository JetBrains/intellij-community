package org.jetbrains.jewel.foundation.modifier

import androidx.compose.ui.Modifier
import androidx.compose.ui.awt.awtEventOrNull
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.SuspendingPointerInputModifierNode
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.node.DelegatingNode
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.platform.InspectorInfo
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
public fun Modifier.onMove(onMove: (MouseEvent?) -> Unit): Modifier = this then OnMoveElement(onMove)

private class OnMoveElement(val onMove: (MouseEvent?) -> Unit) : ModifierNodeElement<OnMoveNode>() {
    override fun create(): OnMoveNode = OnMoveNode(onMove)

    override fun update(node: OnMoveNode) {
        node.onMove = onMove
    }

    override fun InspectorInfo.inspectableProperties() {
        name = "onMove"
        properties["onMove"] = onMove
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is OnMoveElement) return false
        return onMove == other.onMove
    }

    override fun hashCode(): Int = onMove.hashCode()
}

private class OnMoveNode(var onMove: (MouseEvent?) -> Unit) : DelegatingNode() {
    init {
        delegate(
            SuspendingPointerInputModifierNode {
                awaitPointerEventScope {
                    var previousPosition: Offset? = null
                    while (true) {
                        val event = awaitPointerEvent()
                        val position = event.changes.firstOrNull()?.position
                        when (event.type) {
                            PointerEventType.Exit -> previousPosition = null
                            PointerEventType.Move -> {
                                if (position != previousPosition) {
                                    previousPosition = position
                                    onMove(event.awtEventOrNull)
                                }
                            }
                            else -> previousPosition = position
                        }
                    }
                }
            }
        )
    }
}
