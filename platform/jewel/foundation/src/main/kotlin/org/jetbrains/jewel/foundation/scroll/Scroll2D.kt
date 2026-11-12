// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jewel.foundation.scroll

import androidx.compose.foundation.gestures.FlingBehavior
import androidx.compose.foundation.gestures.scrollable2D
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.IntrinsicMeasurable
import androidx.compose.ui.layout.IntrinsicMeasureScope
import androidx.compose.ui.layout.Measurable
import androidx.compose.ui.layout.MeasureResult
import androidx.compose.ui.layout.MeasureScope
import androidx.compose.ui.node.LayoutModifierNode
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.node.SemanticsModifierNode
import androidx.compose.ui.platform.InspectorInfo
import androidx.compose.ui.semantics.ScrollAxisRange
import androidx.compose.ui.semantics.SemanticsPropertyReceiver
import androidx.compose.ui.semantics.horizontalScrollAxisRange
import androidx.compose.ui.semantics.isTraversalGroup
import androidx.compose.ui.semantics.scrollBy
import androidx.compose.ui.semantics.scrollByOffset
import androidx.compose.ui.semantics.verticalScrollAxisRange
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import kotlinx.coroutines.launch
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.jewel.foundation.ExperimentalJewelApi

/**
 * Lets the content scroll on both axes at the same time, when the content is larger than the maximum constraints. It is
 * the 2D equivalent of [androidx.compose.foundation.verticalScroll] and [androidx.compose.foundation.horizontalScroll].
 *
 * The content has no size limit on either axis. The maximum constraints of the element must be finite on the two axes.
 *
 * In a right-to-left layout, the content starts at the right edge and the horizontal scroll direction is reversed.
 *
 * This modifier does not scroll a focused child into view, and it does not handle the Page Up and Page Down keys.
 *
 * @param state The state of the scroll.
 * @param enabled Whether the user can scroll with a gesture or with the mouse wheel. A scroll through [state] still
 *   works when this value is false.
 * @param flingBehavior The behavior after the end of a drag. When null, the default of
 *   [androidx.compose.foundation.gestures.scrollable2D] applies.
 */
@ExperimentalJewelApi
@ApiStatus.Experimental
public fun Modifier.scroll2D(
    state: Scroll2DState,
    enabled: Boolean = true,
    flingBehavior: FlingBehavior? = null,
): Modifier =
    clipToBounds()
        // The semantics node nearest to the head applies last. Thus the scroll actions of this node replace the
        // actions of scrollable2D only when it comes first.
        .then(Scroll2DSemanticsElement(state, enabled))
        .scrollable2D(state = state, enabled = enabled, flingBehavior = flingBehavior)
        .then(Scroll2DLayoutElement(state))

private data class Scroll2DSemanticsElement(private val state: Scroll2DState, private val enabled: Boolean) :
    ModifierNodeElement<Scroll2DSemanticsNode>() {
    override fun create(): Scroll2DSemanticsNode = Scroll2DSemanticsNode(state, enabled)

    override fun update(node: Scroll2DSemanticsNode) {
        node.state = state
        node.enabled = enabled
    }

    override fun InspectorInfo.inspectableProperties() {
        name = "scroll2DSemantics"
        properties["state"] = state
        properties["enabled"] = enabled
    }
}

/**
 * Publishes the scroll position, and scroll actions that take a delta in the direction of [Scroll2DState.value]. An
 * accessibility service and `performScrollTo` read the position and send that kind of delta. The actions of
 * `scrollable2D` expect a different one: the distance that the content moves on the screen.
 */
private class Scroll2DSemanticsNode(var state: Scroll2DState, var enabled: Boolean) :
    Modifier.Node(), SemanticsModifierNode {
    private val scrollByAction: (Float, Float) -> Boolean = { x, y ->
        coroutineScope.launch { state.animateScrollByValue(Offset(x, y)) }
        true
    }

    private val scrollByOffsetAction: suspend (Offset) -> Offset = { offset -> state.animateScrollByValue(offset) }

    override fun SemanticsPropertyReceiver.applySemantics() {
        isTraversalGroup = true
        horizontalScrollAxisRange =
            ScrollAxisRange(value = { state.value.x.toFloat() }, maxValue = { state.maxValue.x.toFloat() })
        verticalScrollAxisRange =
            ScrollAxisRange(value = { state.value.y.toFloat() }, maxValue = { state.maxValue.y.toFloat() })
        if (enabled) {
            scrollBy(action = scrollByAction)
            scrollByOffset(action = scrollByOffsetAction)
        }
    }
}

private data class Scroll2DLayoutElement(private val state: Scroll2DState) : ModifierNodeElement<Scroll2DLayoutNode>() {
    override fun create(): Scroll2DLayoutNode = Scroll2DLayoutNode(state)

    override fun update(node: Scroll2DLayoutNode) {
        node.state = state
    }

    override fun InspectorInfo.inspectableProperties() {
        name = "scroll2D"
        properties["state"] = state
    }
}

private class Scroll2DLayoutNode(var state: Scroll2DState) : Modifier.Node(), LayoutModifierNode {
    override fun MeasureScope.measure(measurable: Measurable, constraints: Constraints): MeasureResult {
        check(constraints.hasBoundedWidth && constraints.hasBoundedHeight) {
            "Modifier.scroll2D was measured with an infinite maximum width or height, which is not supported. " +
                "A common reason is nesting it inside another scrollable layout. " +
                "Give the element a finite size on the two axes."
        }

        val placeable =
            measurable.measure(constraints.copy(maxWidth = Constraints.Infinity, maxHeight = Constraints.Infinity))
        val width = placeable.width.coerceAtMost(constraints.maxWidth)
        val height = placeable.height.coerceAtMost(constraints.maxHeight)
        val max = IntOffset(placeable.width - width, placeable.height - height)

        // Set the maximum before the layout block, so that a scroll during the same measure pass can reach it.
        state.maxValue = max
        state.viewportSize = IntSize(width, height)
        state.contentSize = IntSize(placeable.width, placeable.height)
        state.isRtl = layoutDirection == LayoutDirection.Rtl

        return layout(width, height) {
            val scroll = state.value
            val x = scroll.x.coerceIn(0, max.x)
            val y = scroll.y.coerceIn(0, max.y)
            // The relative placement mirrors the x coordinate in a right-to-left layout.
            withMotionFrameOfReferencePlacement { placeable.placeRelativeWithLayer(-x, -y) }
        }
    }

    override fun IntrinsicMeasureScope.minIntrinsicWidth(measurable: IntrinsicMeasurable, height: Int): Int =
        measurable.minIntrinsicWidth(Constraints.Infinity)

    override fun IntrinsicMeasureScope.minIntrinsicHeight(measurable: IntrinsicMeasurable, width: Int): Int =
        measurable.minIntrinsicHeight(Constraints.Infinity)

    override fun IntrinsicMeasureScope.maxIntrinsicWidth(measurable: IntrinsicMeasurable, height: Int): Int =
        measurable.maxIntrinsicWidth(Constraints.Infinity)

    override fun IntrinsicMeasureScope.maxIntrinsicHeight(measurable: IntrinsicMeasurable, width: Int): Int =
        measurable.maxIntrinsicHeight(Constraints.Infinity)
}
