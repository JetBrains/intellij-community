// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jewel.foundation.scroll

import androidx.compose.foundation.MutatePriority
import androidx.compose.foundation.ScrollIndicatorState
import androidx.compose.foundation.gestures.ScrollScope
import androidx.compose.foundation.gestures.Scrollable2DState
import androidx.compose.foundation.gestures.ScrollableState
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.ui.geometry.Offset

/**
 * A one-axis [ScrollableState] view of a [Scrollable2DState].
 *
 * The view has no mutex of its own. Each scroll goes through [parent], so a scroll on this axis and a scroll of the
 * parent cancel each other.
 *
 * The axis delta is positive when the content scrolls forward, as in [androidx.compose.foundation.ScrollState].
 *
 * @param parent The 2D state that does the scroll.
 * @param axisToRaw Converts an axis delta to a delta of [parent].
 * @param rawToAxis Converts a delta of [parent] to an axis delta.
 * @param canScrollForwardState Whether the content can scroll forward on this axis.
 * @param canScrollBackwardState Whether the content can scroll backward on this axis.
 * @param indicatorState The metrics of this axis for a scroll indicator, or null when the metrics are not available.
 */
internal class AxisScrollableState(
    private val parent: Scrollable2DState,
    private val axisToRaw: (Float) -> Offset,
    private val rawToAxis: (Offset) -> Float,
    canScrollForwardState: State<Boolean>,
    canScrollBackwardState: State<Boolean>,
    private val indicatorState: ScrollIndicatorState?,
) : ScrollableState {
    override suspend fun scroll(scrollPriority: MutatePriority, block: suspend ScrollScope.() -> Unit) {
        parent.scroll(scrollPriority) {
            val parentScope = this
            val axisScope =
                object : ScrollScope {
                    override fun scrollBy(pixels: Float): Float {
                        if (pixels.isNaN()) return 0f
                        return rawToAxis(parentScope.scrollBy(axisToRaw(pixels)))
                    }
                }
            axisScope.block()
        }
    }

    override fun dispatchRawDelta(delta: Float): Float = rawToAxis(parent.dispatchRawDelta(axisToRaw(delta)))

    override val isScrollInProgress: Boolean
        get() = parent.isScrollInProgress

    override val canScrollForward: Boolean by canScrollForwardState

    override val canScrollBackward: Boolean by canScrollBackwardState

    override val scrollIndicatorState: ScrollIndicatorState?
        get() = indicatorState
}
