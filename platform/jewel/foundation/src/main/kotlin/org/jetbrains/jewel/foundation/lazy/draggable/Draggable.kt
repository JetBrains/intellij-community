// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jewel.foundation.lazy.draggable

import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.modifier.ModifierLocal
import androidx.compose.ui.modifier.modifierLocalConsumer
import androidx.compose.ui.modifier.modifierLocalOf
import androidx.compose.ui.modifier.modifierLocalProvider
import androidx.compose.ui.zIndex

internal val ModifierLocalDraggableLayoutOffset = modifierLocalOf { Offset.Zero }

@Suppress("ModifierComposed") // To fix in JEWEL-921
internal fun Modifier.draggableLayout(): Modifier = composed {
    var offset by remember { mutableStateOf(Offset.Zero) }

    this.onGloballyPositioned { offset = it.positionInRoot() }
        .modifierLocalProvider(ModifierLocalDraggableLayoutOffset) { offset }
}

@Suppress("ModifierComposed") // To fix in JEWEL-921
internal fun Modifier.draggingGestures(stateLocal: ModifierLocal<LazyLayoutDraggingState<*>>, key: Any?): Modifier =
    composed {
        var state by remember { mutableStateOf<LazyLayoutDraggingState<*>?>(null) }
        var itemOffset by remember { mutableStateOf(Offset.Zero) }
        var layoutOffset by remember { mutableStateOf(Offset.Zero) }

        modifierLocalConsumer {
                state = stateLocal.current
                layoutOffset = ModifierLocalDraggableLayoutOffset.current
            }
            .then(
                if (state != null) {
                    Modifier.onGloballyPositioned { itemOffset = it.positionInRoot() }
                        .pointerInput(Unit) {
                            detectDragGestures(
                                onDrag = { change, offset ->
                                    change.consume()
                                    state?.onDrag(offset)
                                },
                                onDragStart = { state?.onDragStart(key, it + itemOffset - layoutOffset) },
                                onDragEnd = { state?.onDragInterrupted() },
                                onDragCancel = { state?.onDragInterrupted() },
                            )
                        }
                } else {
                    Modifier
                }
            )
    }

@Suppress("ModifierComposed") // To fix in JEWEL-921
internal fun Modifier.draggingOffset(
    stateLocal: ModifierLocal<LazyLayoutDraggingState<*>>,
    key: Any?,
    orientation: Orientation? = null,
): Modifier = composed {
    var state by remember { mutableStateOf<LazyLayoutDraggingState<*>?>(null) }
    val dragging = state?.draggingItemKey == key

    this.modifierLocalConsumer { state = stateLocal.current }
        .then(
            if (state != null && dragging) {
                Modifier.zIndex(2f)
                    .graphicsLayer(
                        translationX =
                            if (orientation == Orientation.Vertical) {
                                0f
                            } else {
                                state?.draggingItemOffsetTransformX ?: 0f
                            },
                        translationY =
                            if (orientation == Orientation.Horizontal) {
                                0f
                            } else {
                                state?.draggingItemOffsetTransformY ?: 0f
                            },
                    )
            } else {
                Modifier
            }
        )
}
