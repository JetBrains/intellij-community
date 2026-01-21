// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jewel.foundation.lazy.draggable

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size

public abstract class LazyLayoutDraggingState<T> {
    public var draggingItemOffsetTransformX: Float by mutableFloatStateOf(0f)

    public var draggingItemOffsetTransformY: Float by mutableFloatStateOf(0f)

    public var draggingItemKey: Any? by mutableStateOf(null)

    public var initialOffset: Offset = Offset.Zero

    public var draggingOffset: Offset = Offset.Zero

    internal val interactionSource: MutableInteractionSource = MutableInteractionSource()

    public fun onDragStart(key: Any?, offset: Offset) {
        draggingItemKey = key
        initialOffset = offset
        draggingOffset = Offset.Zero
        draggingItemOffsetTransformX = 0f
        draggingItemOffsetTransformY = 0f
    }

    public fun onDrag(offset: Offset) {
        draggingItemOffsetTransformX += offset.x
        draggingItemOffsetTransformY += offset.y
        draggingOffset += offset

        val draggingItem = getItemWithKey(draggingItemKey ?: return) ?: return
        val hoverItem = getReplacingItem(draggingItem)

        if (hoverItem != null && draggingItem.key != hoverItem.key) {
            val targetOffset =
                if (draggingItem.index < hoverItem.index) {
                    val maxOffset = hoverItem.offset + Offset(hoverItem.size.width, hoverItem.size.height)
                    maxOffset - Offset(draggingItem.size.width, draggingItem.size.height)
                } else {
                    hoverItem.offset
                }

            val changedOffset = draggingItem.offset - targetOffset

            if (moveItem(draggingItem.key, hoverItem.key)) {
                draggingItemOffsetTransformX += changedOffset.x
                draggingItemOffsetTransformY += changedOffset.y
            }
        }
    }

    public fun onDragInterrupted() {
        draggingItemKey = null
        initialOffset = Offset.Zero
        draggingOffset = Offset.Zero
        draggingItemOffsetTransformX = 0f
        draggingItemOffsetTransformY = 0f
    }

    public abstract fun canMove(key: Any?): Boolean

    public abstract fun moveItem(from: Any?, to: Any?): Boolean

    public abstract fun getReplacingItem(draggingItem: T): T?

    public abstract fun getItemWithKey(key: Any): T?

    public abstract val T.offset: Offset

    public abstract val T.size: Size

    public abstract val T.index: Int

    public abstract val T.key: Any?
}
