// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jewel.foundation.scroll

import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.SpringSpec
import androidx.compose.foundation.MutatePriority
import androidx.compose.foundation.MutatorMutex
import androidx.compose.foundation.ScrollIndicatorState
import androidx.compose.foundation.gestures.Scroll2DScope
import androidx.compose.foundation.gestures.Scrollable2DState
import androidx.compose.foundation.gestures.ScrollableState
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.toOffset
import kotlin.math.roundToInt
import kotlinx.coroutines.coroutineScope
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.jewel.foundation.ExperimentalJewelApi

/**
 * Creates and remembers a [Scroll2DState]. The state survives a configuration change and a process restart.
 *
 * @param initial The initial scroll position, in pixels.
 */
@ExperimentalJewelApi
@ApiStatus.Experimental
@Composable
public fun rememberScroll2DState(initial: IntOffset = IntOffset.Zero): Scroll2DState =
    rememberSaveable(saver = Scroll2DState.Saver) { Scroll2DState(initial) }

/**
 * The state of content that scrolls on both axes at the same time. It is the 2D equivalent of
 * [androidx.compose.foundation.ScrollState]. Use it with [scroll2D].
 *
 * The [value] increases when the content scrolls forward: down on the vertical axis, and toward the end edge on the
 * horizontal axis. So the horizontal [value] is zero when the start edge of the content is visible, in both
 * left-to-right and right-to-left layouts.
 *
 * The functions of [Scrollable2DState] take the distance that the content moves on the screen, as
 * [scrollable2D][androidx.compose.foundation.gestures.scrollable2D] reports it. These are [scroll], [dispatchRawDelta],
 * and the `scrollBy` and `animateScrollBy` extensions. [androidx.compose.foundation.ScrollState] uses a different sign:
 * its delta is positive when the content scrolls forward. Here, a positive y delta moves the content down on the screen
 * and shows the content above it, so [value] decreases. To scroll in the direction of [value], use [scrollTo],
 * [animateScrollTo], or the axis views [horizontalScrollableState] and [verticalScrollableState].
 *
 * @param initial The initial scroll position, in pixels.
 */
@ExperimentalJewelApi
@ApiStatus.Experimental
@Stable
public class Scroll2DState(initial: IntOffset = IntOffset.Zero) : Scrollable2DState {
    /** The current scroll position, in pixels. */
    public var value: IntOffset by mutableStateOf(initial)
        private set

    private var maxValueState by mutableStateOf(IntOffset(Int.MAX_VALUE, Int.MAX_VALUE))

    /** The maximum of [value]. Each axis is [Int.MAX_VALUE] until the first measure pass. */
    public var maxValue: IntOffset
        get() = maxValueState
        internal set(newMax) {
            maxValueState = newMax
            Snapshot.withoutReadObservation {
                val current = value
                if (current.x > newMax.x || current.y > newMax.y) {
                    value = IntOffset(current.x.coerceAtMost(newMax.x), current.y.coerceAtMost(newMax.y))
                }
            }
        }

    /** The size of the viewport, in pixels. It is [IntSize.Zero] until the first measure pass. */
    public var viewportSize: IntSize by mutableStateOf(IntSize.Zero)
        internal set

    internal var contentSize: IntSize by mutableStateOf(IntSize.Zero)

    /**
     * `scrollable2D` does not reverse the horizontal direction in a right-to-left layout, so the state does. The layout
     * node of [scroll2D] sets this value in each measure pass.
     */
    internal var isRtl: Boolean by mutableStateOf(false)

    // The scroll deltas are floats and the position is in whole pixels. This keeps the fraction for the next delta.
    private var accumulator = Offset.Zero

    private val scrollMutex = MutatorMutex()
    private var isScrolling by mutableStateOf(false)

    private val scrollScope =
        object : Scroll2DScope {
            override fun scrollBy(delta: Offset): Offset = consumeRawDelta(delta)
        }

    override suspend fun scroll(scrollPriority: MutatePriority, block: suspend Scroll2DScope.() -> Unit): Unit =
        coroutineScope {
            scrollMutex.mutateWith(scrollScope, scrollPriority) {
                isScrolling = true
                try {
                    block()
                } finally {
                    isScrolling = false
                }
            }
        }

    override fun dispatchRawDelta(delta: Offset): Offset = consumeRawDelta(delta)

    override val isScrollInProgress: Boolean
        get() = isScrolling

    override fun canScroll(offset: Offset): Boolean {
        val delta = offset.rawToValueSpace()
        val current = value
        val max = maxValue
        return (delta.x > 0f && current.x < max.x) ||
            (delta.x < 0f && current.x > 0) ||
            (delta.y > 0f && current.y < max.y) ||
            (delta.y < 0f && current.y > 0)
    }

    /**
     * A view of the horizontal axis. Use it for an API that takes a one-axis [ScrollableState], such as a scrollbar. A
     * scroll through this view and a scroll through this state cancel each other, because they use the same mutex.
     */
    public val horizontalScrollableState: ScrollableState =
        AxisScrollableState(
            parent = this,
            axisToRaw = { Offset(it, 0f).rawToValueSpace() },
            rawToAxis = { it.rawToValueSpace().x },
            canScrollForwardState = derivedStateOf { value.x < maxValue.x },
            canScrollBackwardState = derivedStateOf { value.x > 0 },
            indicatorState =
                object : ScrollIndicatorState {
                    override val scrollOffset: Int
                        get() = value.x

                    override val contentSize: Int
                        get() = this@Scroll2DState.contentSize.width

                    override val viewportSize: Int
                        get() = this@Scroll2DState.viewportSize.width
                },
        )

    /**
     * A view of the vertical axis. Use it for an API that takes a one-axis [ScrollableState], such as a scrollbar. A
     * scroll through this view and a scroll through this state cancel each other, because they use the same mutex.
     */
    public val verticalScrollableState: ScrollableState =
        AxisScrollableState(
            parent = this,
            axisToRaw = { Offset(0f, it).rawToValueSpace() },
            rawToAxis = { it.rawToValueSpace().y },
            canScrollForwardState = derivedStateOf { value.y < maxValue.y },
            canScrollBackwardState = derivedStateOf { value.y > 0 },
            indicatorState =
                object : ScrollIndicatorState {
                    override val scrollOffset: Int
                        get() = value.y

                    override val contentSize: Int
                        get() = this@Scroll2DState.contentSize.height

                    override val viewportSize: Int
                        get() = this@Scroll2DState.viewportSize.height
                },
        )

    /**
     * Jumps to the given position.
     *
     * @param value The target position, in pixels. The state limits it to the range from zero to [maxValue].
     */
    public suspend fun scrollTo(value: IntOffset) {
        scrollBy((value - this.value).toOffset().rawToValueSpace())
    }

    /**
     * Scrolls to the given position with an animation.
     *
     * @param value The target position, in pixels. The state limits it to the range from zero to [maxValue].
     * @param animationSpec The animation to use.
     */
    public suspend fun animateScrollTo(value: IntOffset, animationSpec: AnimationSpec<Offset> = SpringSpec()) {
        animateScrollBy((value - this.value).toOffset().rawToValueSpace(), animationSpec)
    }

    /**
     * Scrolls by [delta] with an animation, and returns the consumed part. Both values are in the space of [value], so
     * a positive delta scrolls forward. The semantics scroll actions of [scroll2D] use this function.
     */
    internal suspend fun animateScrollByValue(delta: Offset): Offset =
        animateScrollBy(delta.rawToValueSpace()).rawToValueSpace()

    private fun consumeRawDelta(rawDelta: Offset): Offset {
        if (rawDelta.x.isNaN() || rawDelta.y.isNaN()) return Offset.Zero
        return consumeValueDelta(rawDelta.rawToValueSpace()).rawToValueSpace()
    }

    private fun consumeValueDelta(delta: Offset): Offset {
        val current = value
        val max = maxValue

        val absoluteX = current.x + delta.x + accumulator.x
        val absoluteY = current.y + delta.y + accumulator.y
        val newX = absoluteX.coerceIn(0f, max.x.toFloat())
        val newY = absoluteY.coerceIn(0f, max.y.toFloat())

        val consumedX = newX - current.x
        val consumedY = newY - current.y
        val consumedIntX = consumedX.roundToInt()
        val consumedIntY = consumedY.roundToInt()

        if (consumedIntX != 0 || consumedIntY != 0) {
            value = IntOffset(current.x + consumedIntX, current.y + consumedIntY)
        }
        accumulator = Offset(consumedX - consumedIntX, consumedY - consumedIntY)

        // Report the input delta when the limit did not stop the scroll, to prevent a rounding error.
        return Offset(if (absoluteX != newX) consumedX else delta.x, if (absoluteY != newY) consumedY else delta.y)
    }

    /**
     * Converts between the delta of `scrollable2D` and the delta of [value]. `scrollable2D` reports how far the content
     * moves, which is the opposite of how far [value] changes. The same function converts in the two directions.
     */
    private fun Offset.rawToValueSpace(): Offset = Offset(if (isRtl) x else -x, -y)

    /** Holds the [Saver] of [Scroll2DState]. */
    public companion object {
        /** The default [Saver] for [Scroll2DState]. */
        public val Saver: Saver<Scroll2DState, *> =
            listSaver(save = { listOf(it.value.x, it.value.y) }, restore = { Scroll2DState(IntOffset(it[0], it[1])) })
    }
}
