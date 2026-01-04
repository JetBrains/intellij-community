// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jewel.foundation.lazy.table

import androidx.compose.animation.core.AnimationState
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.animateTo
import androidx.compose.animation.core.copy
import androidx.compose.foundation.gestures.ScrollScope
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastForEach
import androidx.compose.ui.util.fastMaxOfOrNull
import androidx.compose.ui.util.fastSumBy
import kotlin.coroutines.cancellation.CancellationException
import kotlin.math.abs

internal interface LazyTableAnimateScrollScope {
    val density: Density

    val firstVisibleLineIndex: Int

    val firstVisibleLineScrollOffset: Int

    val lastVisibleLineIndex: Int

    val lineCount: Int

    fun getTargetLineOffset(index: Int): Int?

    fun getStartLineOffset(): Int

    fun ScrollScope.snapToLine(index: Int, scrollOffset: Int)

    fun expectedDistanceTo(index: Int, targetScrollOffset: Int): Float

    /** defines min number of items that forces scroll to snap if animation did not reach it */
    val numOfLinesForTeleport: Int

    suspend fun scroll(block: suspend ScrollScope.() -> Unit)
}

private val TargetDistance = 2500.dp
private val BoundDistance = 1500.dp
private val MinimumDistance = 50.dp

private class LineFoundInScroll(val lineOffset: Int, val previousAnimation: AnimationState<Float, AnimationVector1D>) :
    CancellationException()

internal suspend fun LazyTableAnimateScrollScope.animateScrollToItem(index: Int, scrollOffset: Int) {
    scroll {
        require(index >= 0) { "Index should be non-negative ($index)" }
        try {
            val targetDistancePx = with(density) { TargetDistance.toPx() }
            val boundDistancePx = with(density) { BoundDistance.toPx() }
            val minDistancePx = with(density) { MinimumDistance.toPx() }
            var loop = true
            var anim = AnimationState(0f)
            val targetItemInitialOffset = getTargetLineOffset(index)
            if (targetItemInitialOffset != null) {
                // It's already visible, just animate directly
                throw LineFoundInScroll(targetItemInitialOffset, anim)
            }
            val forward = index > firstVisibleLineIndex

            fun isOvershot(): Boolean {
                // Did we scroll past the item?
                @Suppress("RedundantIf") // It's way easier to understand the logic this way
                return if (forward) {
                    if (firstVisibleLineIndex > index) {
                        true
                    } else if (firstVisibleLineIndex == index && firstVisibleLineScrollOffset > scrollOffset) {
                        true
                    } else {
                        false
                    }
                } else { // backward
                    if (firstVisibleLineIndex < index) {
                        true
                    } else if (firstVisibleLineIndex == index && firstVisibleLineScrollOffset < scrollOffset) {
                        true
                    } else {
                        false
                    }
                }
            }

            var loops = 1
            while (loop && lineCount > 0) {
                val expectedDistance = expectedDistanceTo(index, scrollOffset)
                val target =
                    if (abs(expectedDistance) < targetDistancePx) {
                        val absTargetPx = maxOf(abs(expectedDistance), minDistancePx)
                        if (forward) absTargetPx else -absTargetPx
                    } else {
                        if (forward) targetDistancePx else -targetDistancePx
                    }

                anim = anim.copy(value = 0f)
                var prevValue = 0f
                anim.animateTo(target, sequentialAnimation = (anim.velocity != 0f)) {
                    // If we haven't found the item yet, check if it's visible.
                    var targetItemOffset = getTargetLineOffset(index)

                    if (targetItemOffset == null) {
                        // Springs can overshoot their target, clamp to the desired range
                        val coercedValue =
                            if (target > 0) {
                                value.coerceAtMost(target)
                            } else {
                                value.coerceAtLeast(target)
                            }
                        val delta = coercedValue - prevValue

                        val consumed = scrollBy(delta)
                        targetItemOffset = getTargetLineOffset(index)
                        if (targetItemOffset != null) {
                            //                            debugLog { "Found the item after performing scrollBy()"
                            // }
                        } else if (!isOvershot()) {
                            if (delta != consumed) {
                                cancelAnimation()
                                loop = false
                                return@animateTo
                            }
                            prevValue += delta
                            if (forward) {
                                if (value > boundDistancePx) {
                                    cancelAnimation()
                                }
                            } else {
                                if (value < -boundDistancePx) {
                                    cancelAnimation()
                                }
                            }

                            if (forward) {
                                if (loops >= 2 && index - lastVisibleLineIndex > numOfLinesForTeleport) {
                                    // Teleport
                                    snapToLine(index = index - numOfLinesForTeleport, scrollOffset = 0)
                                }
                            } else {
                                if (loops >= 2 && firstVisibleLineIndex - index > numOfLinesForTeleport) {
                                    // Teleport
                                    snapToLine(index = index + numOfLinesForTeleport, scrollOffset = 0)
                                }
                            }
                        }
                    }

                    // We don't throw LineFoundInScroll when we snap, because once we've snapped to
                    // the final position, there's no need to animate to it.
                    if (isOvershot()) {
                        snapToLine(index = index, scrollOffset = scrollOffset)
                        loop = false
                        cancelAnimation()
                        return@animateTo
                    } else if (targetItemOffset != null) {
                        throw LineFoundInScroll(targetItemOffset, anim)
                    }
                }

                loops++
            }
        } catch (itemFound: LineFoundInScroll) {
            // We found it, animate to it
            // Bring to the requested position - will be automatically stopped if not possible
            val anim = itemFound.previousAnimation.copy(value = 0f)
            val target = (itemFound.lineOffset + scrollOffset - getStartLineOffset()).toFloat()
            var prevValue = 0f
            anim.animateTo(target, sequentialAnimation = (anim.velocity != 0f)) {
                // Springs can overshoot their target, clamp to the desired range
                val coercedValue =
                    when {
                        target > 0 -> {
                            value.coerceAtMost(target)
                        }

                        target < 0 -> {
                            value.coerceAtLeast(target)
                        }

                        else -> {
                            0f
                        }
                    }
                val delta = coercedValue - prevValue
                val consumed = scrollBy(delta)
                if (
                    delta != consumed || coercedValue != value // hit the end or would have overshot, stop
                ) {
                    cancelAnimation()
                }
                prevValue += delta
            }
            // Once we're finished the animation, snap to the exact position to account for
            // rounding error (otherwise we tend to end up with the previous item scrolled the
            // tiniest bit onscreen)
            // TODO: prevent temporarily scrolling *past* the item
            snapToLine(index = index, scrollOffset = scrollOffset)
        }
    }
}

internal class LazyTableAnimateHorizontalScrollScope(private val state: LazyTableState) : LazyTableAnimateScrollScope {
    override val density: Density
        get() = state.density

    override val firstVisibleLineIndex: Int
        get() = state.firstVisibleColumnIndex

    override val firstVisibleLineScrollOffset: Int
        get() = state.firstVisibleItemHorizontalScrollOffset

    override val lastVisibleLineIndex: Int
        get() =
            state.layoutInfo.pinnedRowsInfo.lastOrNull()?.column
                ?: state.layoutInfo.floatingItemsInfo.lastOrNull()?.column
                ?: 0

    override val lineCount: Int
        get() = state.layoutInfo.columns

    override val numOfLinesForTeleport: Int = 100

    override fun getTargetLineOffset(index: Int): Int? {
        if (state.layoutInfo.pinnedColumns > index) {
            return 0
        }

        state.layoutInfo.floatingItemsInfo.fastForEach {
            if (it.column == index) {
                return it.offset.x
            }
        }
        return null
    }

    override fun getStartLineOffset(): Int =
        (state.layoutInfo.pinnedItemsInfo.fastMaxOfOrNull { it.offset.x + it.size.width } ?: 0) +
            state.layoutInfo.horizontalSpacing

    override fun ScrollScope.snapToLine(index: Int, scrollOffset: Int) {
        state.snapToColumnInternal(index, scrollOffset, forceRemeasure = true)
    }

    override fun expectedDistanceTo(index: Int, targetScrollOffset: Int): Float {
        if (state.layoutInfo.pinnedColumns > index) {
            return 0f
        }

        val layoutInfo = state.layoutInfo
        val visibleItems = layoutInfo.floatingItemsInfo
        if (visibleItems.isEmpty()) {
            return 0f
        }

        val averageSize = visibleItems.fastSumBy { it.size.width } / visibleItems.size + layoutInfo.horizontalSpacing
        val indexesDiff = index - firstVisibleLineIndex
        var coercedOffset = minOf(abs(targetScrollOffset), averageSize)
        if (targetScrollOffset < 0) coercedOffset *= -1
        return (averageSize * indexesDiff).toFloat() + coercedOffset - firstVisibleLineScrollOffset
    }

    override suspend fun scroll(block: suspend ScrollScope.() -> Unit) {
        state.horizontalScroll(block = block)
    }
}

internal class LazyTableAnimateVerticalScrollScope(private val state: LazyTableState) : LazyTableAnimateScrollScope {
    override val density: Density
        get() = state.density

    override val firstVisibleLineIndex: Int
        get() = state.firstVisibleRowIndex

    override val firstVisibleLineScrollOffset: Int
        get() = state.firstVisibleItemVerticalScrollOffset

    override val lastVisibleLineIndex: Int
        get() =
            state.layoutInfo.pinnedColumnsInfo.lastOrNull()?.row
                ?: state.layoutInfo.floatingItemsInfo.lastOrNull()?.row
                ?: 0

    override val lineCount: Int
        get() = state.layoutInfo.rows

    override val numOfLinesForTeleport: Int = 100

    override fun getTargetLineOffset(index: Int): Int? {
        if (state.layoutInfo.pinnedRows > index) {
            return 0
        }

        state.layoutInfo.floatingItemsInfo.fastForEach {
            if (it.row == index) {
                return it.offset.y
            }
        }
        return null
    }

    override fun getStartLineOffset(): Int =
        (state.layoutInfo.pinnedItemsInfo.fastMaxOfOrNull { it.offset.y + it.size.height } ?: 0) +
            state.layoutInfo.verticalSpacing

    override fun ScrollScope.snapToLine(index: Int, scrollOffset: Int) {
        state.snapToRowInternal(index, scrollOffset, forceRemeasure = true)
    }

    override fun expectedDistanceTo(index: Int, targetScrollOffset: Int): Float {
        if (state.layoutInfo.pinnedRows > index) {
            return 0f
        }

        val layoutInfo = state.layoutInfo
        val visibleItems = layoutInfo.floatingItemsInfo
        if (visibleItems.isEmpty()) {
            return 0f
        }

        val averageSize = visibleItems.fastSumBy { it.size.height } / visibleItems.size + layoutInfo.verticalSpacing
        val indexesDiff = index - firstVisibleLineIndex
        var coercedOffset = minOf(abs(targetScrollOffset), averageSize)
        if (targetScrollOffset < 0) coercedOffset *= -1
        return (averageSize * indexesDiff).toFloat() + coercedOffset - firstVisibleLineScrollOffset
    }

    override suspend fun scroll(block: suspend ScrollScope.() -> Unit) {
        state.verticalScroll(block = block)
    }
}
