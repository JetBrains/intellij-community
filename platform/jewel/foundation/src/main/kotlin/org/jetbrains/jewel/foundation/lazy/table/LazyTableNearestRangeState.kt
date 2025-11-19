// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jewel.foundation.lazy.table

import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.structuralEqualityPolicy

internal class LazyTableNearestRangeState(
    firstVisibleItem: Int,
    lastVisibleItem: Int,
    private val extraItemCount: Int,
) : State<IntRange> {
    override var value: IntRange by
        mutableStateOf(
            calculateNearestItemsRange(firstVisibleItem, lastVisibleItem, extraItemCount),
            structuralEqualityPolicy(),
        )
        private set

    private var lastFirstVisibleItem = firstVisibleItem
    private var lastLastVisibleItem = lastVisibleItem

    fun update(firstVisibleItem: Int, lastVisibleItem: Int) {
        val range = value
        if (firstVisibleItem in range && lastVisibleItem in range) return

        lastFirstVisibleItem = firstVisibleItem
        lastLastVisibleItem = lastVisibleItem
        value = calculateNearestItemsRange(firstVisibleItem, lastVisibleItem, extraItemCount)
    }

    private companion object {
        /**
         * Returns a range of indexes, which contains at least [extraItemCount] items near the first visible item. It is
         * optimized to return the same range for small changes in the firstVisibleItem value so we do not regenerate
         * the map on each scroll.
         */
        private fun calculateNearestItemsRange(
            firstVisibleItem: Int,
            lastVisibleItem: Int,
            extraItemCount: Int,
        ): IntRange {
            val start = maxOf(firstVisibleItem - extraItemCount, 0)
            val end = lastVisibleItem + extraItemCount
            return start until end
        }
    }
}
