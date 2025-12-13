// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jewel.foundation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.awt.awtEventOrNull
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.Measurable
import androidx.compose.ui.layout.MeasureResult
import androidx.compose.ui.layout.MeasureScope
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.Constraints
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.delay
import org.jetbrains.annotations.ApiStatus

/**
 * A container that allows its child to temporarily overflow its constraints (width and/or height) when hovered.
 *
 * When the mouse cursor enters the box, the child is remeasured with an effectively unbounded maximum width and/or
 * height (depending on which side would overflow) and placed above surrounding content using [overflowZIndex], provided
 * its intrinsic size exceeds the incoming constraints. When the cursor leaves, or when [overflowEnabled] is `false`,
 * the child is measured with the original constraints and no overflow is shown.
 *
 * The [content] lambda receives an [OverflowBoxScope] that exposes [OverflowBoxScope.isOverflowing], which you can use
 * to adapt visuals while the overflow is active (for example, show a shadow or fade).
 *
 * Behavior notes:
 * - Overflow can affect width, height, or both, depending on the child's intrinsic size relative to the current
 *   constraints.
 * - A small delay (~700 ms) is applied before the overflow becomes visible after the cursor enters.
 * - Overflow occurs only if [overflowEnabled] is `true` and the child's max intrinsic size exceeds the container's
 *   current max constraints.
 *
 * This API is experimental and may change without notice.
 *
 * @param modifier the [Modifier] to be applied to the container.
 * @param overflowEnabled whether the overflow behavior is active. If `false`, the child never overflows.
 * @param contentAlignment alignment of the child within the box.
 * @param overflowZIndex the z-index used while overflowing so the content renders above neighboring nodes.
 * @param content the content of this box. The receiver provides [OverflowBoxScope] utilities.
 */
@ApiStatus.Experimental
@ExperimentalJewelApi
@Composable
public fun OverflowBox(
    modifier: Modifier = Modifier,
    overflowEnabled: Boolean = true,
    contentAlignment: Alignment = Alignment.TopStart,
    overflowZIndex: Float = 1f,
    content: @Composable OverflowBoxScope.() -> Unit,
) {
    val currentOverflowEnabled by rememberUpdatedState(overflowEnabled)
    val currentOverflowZIndex by rememberUpdatedState(overflowZIndex)

    var lastMousePosition by remember { mutableStateOf<java.awt.Point?>(null) }
    var isOverflowVisible by remember { mutableStateOf(false) }

    val onMeasure: MeasureScope.(Measurable, Constraints) -> MeasureResult =
        remember(isOverflowVisible) {
            { measurable, constraints ->
                // Predict intrinsic sizes to determine potential overflow on each axis.
                val predictWidth = measurable.maxIntrinsicWidth(constraints.maxHeight)
                val predictHeight = measurable.maxIntrinsicHeight(constraints.maxWidth)

                val constraintWith = constraints.maxWidth
                val constraintHeight = constraints.maxHeight

                val overflowingX =
                    isOverflowVisible && constraintWith != Constraints.Infinity && predictWidth > constraintWith

                val overflowingY =
                    isOverflowVisible && constraintHeight != Constraints.Infinity && predictHeight > constraintHeight

                val targetConstraints =
                    constraints.copy(
                        maxWidth = if (overflowingX) Constraints.Infinity else constraints.maxWidth,
                        maxHeight = if (overflowingY) Constraints.Infinity else constraints.maxHeight,
                    )

                val zIndex = if (overflowingX || overflowingY) currentOverflowZIndex else 0f

                // Trick for overflow layout: compensate layout alignment by offsetting half of the extra space.
                val xOffset = if (overflowingX) (predictWidth - constraintWith) / 2 else 0
                val yOffset = if (overflowingY) (predictHeight - constraintHeight) / 2 else 0

                val placements = measurable.measure(targetConstraints)
                layout(placements.width, placements.height) { placements.placeRelative(xOffset, yOffset, zIndex) }
            }
        }

    LaunchedEffect(lastMousePosition) {
        if (!currentOverflowEnabled) {
            isOverflowVisible = false
            return@LaunchedEffect
        }

        val shouldShowOverflow = lastMousePosition != null
        if (!isOverflowVisible && shouldShowOverflow) delay(700.milliseconds)
        isOverflowVisible = shouldShowOverflow
    }

    Box(
        Modifier.layout(onMeasure)
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent()
                        lastMousePosition =
                            when (event.type) {
                                PointerEventType.Enter,
                                PointerEventType.Move -> event.awtEventOrNull?.point?.takeIf { currentOverflowEnabled }

                                else -> null
                            }
                    }
                }
            }
            .then(modifier),
        contentAlignment = contentAlignment,
    ) {
        rememberOverflowBoxScope(isOverflowVisible, this).content()
    }
}

public interface OverflowBoxScope : BoxScope {
    /**
     * Whether the child is currently laid out in overflow mode (i.e., measured with relaxed constraints on at least one
     * axis — width and/or height — and visually extending beyond the container's normal max constraints).
     */
    public val isOverflowing: Boolean
}

@Composable
private fun rememberOverflowBoxScope(isOverflowing: Boolean, scope: BoxScope) =
    remember(isOverflowing, scope) {
        object : OverflowBoxScope, BoxScope by scope {
            override val isOverflowing: Boolean = isOverflowing
        }
    }
