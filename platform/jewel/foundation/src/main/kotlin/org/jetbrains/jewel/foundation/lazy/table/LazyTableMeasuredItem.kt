// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jewel.foundation.lazy.table

import androidx.compose.ui.Alignment
import androidx.compose.ui.layout.Placeable
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection

internal class LazyTableMeasuredItem(
    override val index: Int,
    override val row: Int,
    override val column: Int,
    override val size: IntSize,
    private val placeables: List<Placeable>,
    private val alignment: Alignment,
    private val layoutDirection: LayoutDirection,
    private val visualOffset: IntOffset,
    override val key: Any,
    override val contentType: Any?,
) : LazyTableItemInfo {
    private val placeableOffsets: IntArray = IntArray(placeables.size * 2)

    override var offset: IntOffset = IntOffset.Zero
        private set

    fun position(offset: IntOffset) {
        this.offset = offset

        for (index in placeables.indices) {
            val placeable = placeables[index]

            val indexInArray = index * 2

            val alignOffset = alignment.align(IntSize(placeable.width, placeable.height), size, layoutDirection)

            placeableOffsets[indexInArray] = offset.x + alignOffset.x
            placeableOffsets[indexInArray + 1] = offset.y + alignOffset.y
        }
    }

    fun applyScrollDelta(delta: IntOffset) {
        this.offset += delta

        for (index in placeables.indices) {
            val indexInArray = index * 2

            placeableOffsets[indexInArray] += delta.x
            placeableOffsets[indexInArray + 1] += delta.y
        }
    }

    val placeablesCount: Int
        get() = placeables.size

    fun getParentData(index: Int) = placeables[index].parentData

    internal fun getOffset(index: Int) = IntOffset(placeableOffsets[index * 2], placeableOffsets[index * 2 + 1])

    fun place(scope: Placeable.PlacementScope, zIndex: Float = 0f) {
        with(scope) {
            require(offset != Unset) { "position() should be called first" }

            repeat(placeablesCount) { index ->
                val placeable = placeables[index]
                var offset = getOffset(index)
                offset += visualOffset
                placeable.placeRelativeWithLayer(offset, zIndex)
            }
        }
    }
}

private val Unset = IntOffset(Int.MAX_VALUE, Int.MIN_VALUE)
