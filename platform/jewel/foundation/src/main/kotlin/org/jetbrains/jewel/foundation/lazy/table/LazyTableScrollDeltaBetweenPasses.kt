// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jewel.foundation.lazy.table

import androidx.compose.animation.core.AnimationState
import androidx.compose.animation.core.AnimationVector2D
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.animateTo
import androidx.compose.animation.core.copy
import androidx.compose.animation.core.spring
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import kotlin.math.abs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * This class manages the scroll delta between lookahead pass and approach pass. Lookahead pass is the source of truth
 * for scrolling lazy layouts. However, at times during an animation, the items in approach may not be as large as they
 * are in lookahead yet (i.e. these items have not reached their target size). As such, the same scrolling that
 * lookahead accepts may cause back scroll in approach due to the smaller item size at the end of the list. In this
 * situation, we will be taking the amount of back scroll from the approach and gradually animate it down to 0 to avoid
 * any sudden jump in position via [updateScrollDeltaForApproach].
 *
 * This class is a directly copy from the internal version from compose available at
 * androidx.compose.foundation.lazy.layout.LazyLayoutScrollDeltaBetweenPasses
 */
internal class LazyTableScrollDeltaBetweenPasses {
    internal val scrollDeltaBetweenPasses: Offset
        get() = _scrollDeltaBetweenPasses.value

    internal var job: Job? = null

    internal val isActive: Boolean
        get() = _scrollDeltaBetweenPasses.value.let { it.x != 0f || it.y != 0f }

    @Suppress("ktlint:standard:backing-property-naming")
    private var _scrollDeltaBetweenPasses: AnimationState<Offset, AnimationVector2D> =
        AnimationState(Offset.VectorConverter, Offset.Zero, Offset.Zero)

    // Updates the scroll delta between lookahead & post-lookahead pass
    internal fun updateScrollDeltaForApproach(delta: Offset, density: Density, coroutineScope: CoroutineScope) {
        val threshold = with(density) { DeltaThresholdForScrollAnimation.toPx() }
        if (abs(delta.x) <= threshold && abs(delta.y) <= threshold) {
            // If the delta is within the threshold, scroll by the delta amount instead of animating
            return
        }

        // Scroll delta is updated during lookahead, we don't need to trigger lookahead when
        // the delta changes.
        Snapshot.withoutReadObservation {
            val currentDelta = _scrollDeltaBetweenPasses.value

            job?.cancel()

            if (_scrollDeltaBetweenPasses.isRunning) {
                _scrollDeltaBetweenPasses = _scrollDeltaBetweenPasses.copy(currentDelta - delta)
            } else {
                _scrollDeltaBetweenPasses = AnimationState(Offset.VectorConverter, -delta)
            }

            job =
                coroutineScope.launch {
                    _scrollDeltaBetweenPasses.animateTo(
                        Offset.Zero,
                        spring(stiffness = Spring.StiffnessMediumLow, visibilityThreshold = Offset(0.5f, 0.5f)),
                        true,
                    )
                }
        }
    }

    internal fun stop() {
        job?.cancel()
        _scrollDeltaBetweenPasses = AnimationState(Offset.VectorConverter, Offset.Zero)
    }
}

private val DeltaThresholdForScrollAnimation = 1.dp
