// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jewel.ui.component

import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import kotlin.math.sqrt
import org.jetbrains.jewel.ui.component.styling.ScrollbarStyle
import org.jetbrains.jewel.ui.component.styling.ScrollbarVisibility
import org.jetbrains.skiko.OS
import org.jetbrains.skiko.hostOs

/**
 * Pure geometry maths mapping a scroll position onto a scroll indicator thumb.
 *
 * All values are in pixels, and along the scroll axis: "length" is the height of a vertical indicator and the width of
 * a horizontal one. This mirrors the thumb sizing and placement used by [VerticalScrollbar], so that the drawn thumb
 * and the pointer hit testing agree on where the thumb is.
 *
 * @param contentSizePx The total size of the scrollable content along the scroll axis.
 * @param viewportSizePx The size of the visible portion of the content along the scroll axis.
 * @param scrollOffsetPx The current scroll offset from the visual start of the content.
 */
@Immutable
internal data class ScrollIndicatorGeometry(val contentSizePx: Int, val viewportSizePx: Int, val scrollOffsetPx: Int) {
    /**
     * The length of the thumb along the scroll axis, proportional to how much of the content is visible.
     *
     * Returns 0 when there is no track to draw on, or when the content fits the viewport. Otherwise the result is at
     * least [minThumbLengthPx] and never longer than [trackLengthPx].
     */
    fun thumbSizePx(trackLengthPx: Int, minThumbLengthPx: Int): Int {
        if (trackLengthPx <= 0 || contentSizePx <= 0 || viewportSizePx <= 0) return 0

        val visibleFraction = (viewportSizePx.toFloat() / contentSizePx).coerceIn(0f, 1f)
        val minLengthPx = minThumbLengthPx.coerceIn(0, trackLengthPx)
        return (trackLengthPx * visibleFraction).toInt().coerceIn(minLengthPx, trackLengthPx)
    }

    /**
     * The offset of the thumb from the start of the track, proportional to the current scroll position.
     *
     * [ScrollIndicatorState.scrollOffset] already accounts for reverse layout - a reversed list at rest reports its
     * maximum, parking the thumb at the track end - so no mirroring is needed here. Horizontal RTL is the one case
     * upstream leaves to the caller, handled by [ScrollIndicatorAxisGeometry.mirrorMainAxis]. Returns 0 when the
     * content cannot scroll, or when the thumb fills the whole track.
     */
    fun thumbOffsetPx(trackLengthPx: Int, minThumbLengthPx: Int): Int {
        val thumbSizePx = thumbSizePx(trackLengthPx, minThumbLengthPx)
        val extraTrackSpacePx = (trackLengthPx - thumbSizePx).coerceAtLeast(0)
        val extraContentSpacePx = (contentSizePx - viewportSizePx).coerceAtLeast(0)
        if (extraTrackSpacePx == 0 || extraContentSpacePx == 0) return 0

        val scrolledFraction = scrollOffsetPx.coerceIn(0, extraContentSpacePx).toFloat() / extraContentSpacePx
        return (scrolledFraction * extraTrackSpacePx).toInt()
    }

    /**
     * Resolves the thumb's placement within a track that is inset by [startInsetPx] and [endInsetPx] along the scroll
     * axis, so that drawing and hit testing can share one result.
     */
    fun thumbGeometry(
        trackLengthPx: Int,
        startInsetPx: Int,
        endInsetPx: Int,
        minThumbLengthPx: Int,
    ): ScrollIndicatorThumbGeometry {
        val clampedStart = startInsetPx.coerceIn(0, trackLengthPx.coerceAtLeast(0))
        val clampedEnd = endInsetPx.coerceIn(0, (trackLengthPx - clampedStart).coerceAtLeast(0))
        val usableLengthPx = (trackLengthPx - clampedStart - clampedEnd).coerceAtLeast(0)
        return ScrollIndicatorThumbGeometry(
            offsetPx = clampedStart + thumbOffsetPx(usableLengthPx, minThumbLengthPx),
            sizePx = thumbSizePx(usableLengthPx, minThumbLengthPx),
            trackStartPx = clampedStart,
            trackLengthPx = usableLengthPx,
        )
    }
}

/** The resolved position and size of the thumb along the scroll axis, in pixels. */
@Immutable
internal data class ScrollIndicatorThumbGeometry(
    val offsetPx: Int,
    val sizePx: Int,
    val trackStartPx: Int,
    val trackLengthPx: Int,
) {
    /** How far the thumb can travel along the usable track. */
    val travelPx: Int
        get() = (trackLengthPx - sizePx).coerceAtLeast(0)

    /** `true` when [positionPx], measured along the scroll axis, falls on the thumb. */
    fun contains(positionPx: Float): Boolean = positionPx >= offsetPx && positionPx <= offsetPx + sizePx
}

/**
 * The track rectangle for an indicator of the given [orientation], placed at the end of the layout.
 *
 * [ScrollIndicatorState.scrollOffset] is measured from the visual start of the container. For a horizontal scrollable
 * that is the left edge in LTR but the right edge in RTL, so [mirrorMainAxis] is set in that case and the main axis is
 * measured from [Rect.right] backwards. Every consumer goes through [mainAxisOffset] and [thumbRect], so setting the
 * flag here corrects drawing and hit testing together.
 */
@Immutable
internal data class ScrollIndicatorAxisGeometry(
    val orientation: Orientation,
    val bounds: Rect,
    val mirrorMainAxis: Boolean = false,
) {
    /** The track length along the scroll axis. */
    val trackLengthPx: Int
        get() = if (orientation == Orientation.Vertical) bounds.height.toInt() else bounds.width.toInt()

    /** Projects [offset] onto the scroll axis, relative to the track start. */
    fun mainAxisOffset(offset: Offset): Float =
        when {
            orientation == Orientation.Vertical -> offset.y - bounds.top
            mirrorMainAxis -> bounds.right - offset.x
            else -> offset.x - bounds.left
        }

    /** Projects a delta onto the scroll axis. */
    fun mainAxisDelta(offset: Offset): Float = if (orientation == Orientation.Vertical) offset.y else offset.x

    /** `true` when [offset] falls within the track. */
    fun contains(offset: Offset): Boolean = bounds.contains(offset)

    /** The thumb rectangle, inset across the scroll axis by [startInsetPx] and [endInsetPx]. */
    fun thumbRect(offsetPx: Float, lengthPx: Float, startInsetPx: Float, endInsetPx: Float): Rect =
        if (orientation == Orientation.Vertical) {
            Rect(
                left = bounds.left + startInsetPx,
                top = bounds.top + offsetPx,
                right = bounds.right - endInsetPx,
                bottom = bounds.top + offsetPx + lengthPx,
            )
        } else if (mirrorMainAxis) {
            Rect(
                left = bounds.right - offsetPx - lengthPx,
                top = bounds.top + startInsetPx,
                right = bounds.right - offsetPx,
                bottom = bounds.bottom - endInsetPx,
            )
        } else {
            Rect(
                left = bounds.left + offsetPx,
                top = bounds.top + startInsetPx,
                right = bounds.left + offsetPx + lengthPx,
                bottom = bounds.bottom - endInsetPx,
            )
        }
}

/**
 * The track rectangle for an indicator of [thicknessPx] within a node of [size].
 *
 * A vertical indicator sits at the end of the layout — the right edge in LTR and the left edge in RTL — and a
 * horizontal one along the bottom edge.
 */
internal fun scrollIndicatorAxisGeometry(
    size: Size,
    thicknessPx: Float,
    orientation: Orientation,
    layoutDirection: LayoutDirection,
): ScrollIndicatorAxisGeometry {
    val bounds =
        when (orientation) {
            Orientation.Vertical -> {
                val left = if (layoutDirection == LayoutDirection.Ltr) size.width - thicknessPx else 0f
                Rect(left = left, top = 0f, right = left + thicknessPx, bottom = size.height)
            }

            Orientation.Horizontal ->
                Rect(left = 0f, top = size.height - thicknessPx, right = size.width, bottom = size.height)
        }
    // A horizontal scrollable in RTL measures its offset from the right edge, so mirror the main axis.
    val mirrorMainAxis = orientation == Orientation.Horizontal && layoutDirection == LayoutDirection.Rtl
    return ScrollIndicatorAxisGeometry(orientation = orientation, bounds = bounds, mirrorMainAxis = mirrorMainAxis)
}

/** The padding before the track along the scroll axis. */
internal fun PaddingValues.mainAxisStartPadding(orientation: Orientation, layoutDirection: LayoutDirection): Dp =
    if (orientation == Orientation.Vertical) calculateTopPadding() else calculateLeftPadding(layoutDirection)

/** The padding after the track along the scroll axis. */
internal fun PaddingValues.mainAxisEndPadding(orientation: Orientation, layoutDirection: LayoutDirection): Dp =
    if (orientation == Orientation.Vertical) calculateBottomPadding() else calculateRightPadding(layoutDirection)

/** The padding before the track across the scroll axis. */
internal fun PaddingValues.crossAxisStartPadding(orientation: Orientation, layoutDirection: LayoutDirection): Dp =
    if (orientation == Orientation.Vertical) calculateLeftPadding(layoutDirection) else calculateTopPadding()

/** The padding after the track across the scroll axis. */
internal fun PaddingValues.crossAxisEndPadding(orientation: Orientation, layoutDirection: LayoutDirection): Dp =
    if (orientation == Orientation.Vertical) calculateRightPadding(layoutDirection) else calculateBottomPadding()

/**
 * The scroll offset that puts the start of the thumb at [thumbStartPx], measured from the track start.
 *
 * Positioning is absolute rather than incremental, matching the Swing scrollbar: each drag event maps the pointer's
 * current position onto the scroll range, so overshooting an end and coming back cannot drift.
 */
internal fun targetScrollOffsetForThumbStart(
    thumbStartPx: Float,
    trackLengthPx: Int,
    thumbSizePx: Int,
    maxScrollOffsetPx: Int,
): Float {
    val travelPx = (trackLengthPx - thumbSizePx).coerceAtLeast(0)
    if (travelPx == 0 || maxScrollOffsetPx <= 0) return 0f

    return (thumbStartPx.coerceIn(0f, travelPx.toFloat()) / travelPx) * maxScrollOffsetPx
}

/** The scroll offset that centres the thumb on [pointerOffsetPx], for a track click that jumps to the spot. */
internal fun targetScrollOffsetForThumbCenter(
    pointerOffsetPx: Float,
    trackLengthPx: Int,
    thumbSizePx: Int,
    maxScrollOffsetPx: Int,
): Float =
    targetScrollOffsetForThumbStart(
        thumbStartPx = pointerOffsetPx - thumbSizePx / 2f,
        trackLengthPx = trackLengthPx,
        thumbSizePx = thumbSizePx,
        maxScrollOffsetPx = maxScrollOffsetPx,
    )

/**
 * `true` when paging should stop because the thumb has crossed the press going the other way.
 *
 * The direction is latched when the press starts, so the repeat stops instead of paging back and forth. With a plain
 * `ScrollState` the first page always lands the thumb over the press, so this only shows up when lazy-layout size
 * estimates shift mid-gesture.
 */
internal fun pagesBackPastPress(pagingDirection: Int, direction: Int): Boolean =
    pagingDirection != 0 && direction != pagingDirection

/** The paging direction from the thumb towards [pointerOffsetPx]: -1 before it, 1 after it, 0 when on it. */
internal fun pageDirectionTowardPointer(pointerOffsetPx: Float, thumbOffsetPx: Int, thumbSizePx: Int): Int =
    when {
        pointerOffsetPx < thumbOffsetPx -> -1
        pointerOffsetPx > thumbOffsetPx + thumbSizePx -> 1
        else -> 0
    }

/**
 * The layout space a scroll indicator occupies next to the content, rather than over it.
 *
 * This mirrors the reservation the scrollable containers make: on macOS with [ScrollbarVisibility.AlwaysVisible] the
 * scrollbar sits in its own lane beside the content, so that lane must be carved out of the layout. Everywhere else the
 * indicator floats over the content and nothing is reserved.
 *
 * This is deliberately *not* the same thing as [scrollbarContentSafePadding], which tells callers which strip of their
 * own content would be overlaid by a floating scrollbar. Applying that padding stays the caller's decision, since only
 * they know which parts of their content must avoid the scrollbar (text, buttons) and which may sit under it (dividers,
 * backgrounds).
 */
internal fun scrollIndicatorReservedThickness(style: ScrollbarStyle, isMacOs: Boolean): Dp {
    val visibility = style.scrollbarVisibility
    return if (isMacOs && visibility is ScrollbarVisibility.AlwaysVisible) visibility.trackThicknessExpanded else 0.dp
}

/**
 * Reserves [thickness] at the end of the layout along the cross axis of [orientation], so the indicator can be drawn
 * beside the content instead of over it.
 *
 * The child is measured with the reduced size, and the node reports the full size. For a vertical indicator in RTL the
 * child is offset so the reserved lane ends up on the left, matching where the indicator is drawn.
 */
internal fun Modifier.scrollIndicatorReservation(thickness: Dp, orientation: Orientation): Modifier {
    if (thickness <= 0.dp) return this

    return layout { measurable, constraints ->
        val thicknessPx = thickness.roundToPx()
        val isVertical = orientation == Orientation.Vertical
        val childConstraints =
            if (isVertical) {
                constraints.copy(
                    minWidth = (constraints.minWidth - thicknessPx).coerceAtLeast(0),
                    maxWidth = constraints.maxWidth.minusReserved(thicknessPx),
                )
            } else {
                constraints.copy(
                    minHeight = (constraints.minHeight - thicknessPx).coerceAtLeast(0),
                    maxHeight = constraints.maxHeight.minusReserved(thicknessPx),
                )
            }

        val placeable = measurable.measure(childConstraints)
        val width = if (isVertical) placeable.width + thicknessPx else placeable.width
        val height = if (isVertical) placeable.height else placeable.height + thicknessPx
        layout(
            width = width.coerceIn(constraints.minWidth, constraints.maxWidth),
            height = height.coerceIn(constraints.minHeight, constraints.maxHeight),
        ) {
            val x = if (isVertical && layoutDirection == LayoutDirection.Rtl) thicknessPx else 0
            placeable.place(x = x, y = 0)
        }
    }
}

private fun Int.minusReserved(thicknessPx: Int): Int =
    if (this == Constraints.Infinity) Constraints.Infinity else (this - thicknessPx).coerceAtLeast(0)

/**
 * The scroll distance, in pixels, for one wheel notch along [mainAxisSizePx].
 *
 * A block scroll moves a whole viewport, as in Swing. The per-platform line scrolling follows the Compose Multiplatform
 * desktop conventions rather than Swing's own wheel handling, so that wheeling over the reserved lane feels the same as
 * wheeling over the content beside it.
 */
internal fun scrollIndicatorWheelDeltaToPixels(mainAxisSizePx: Int, isBlockScroll: Boolean, density: Density): Float =
    when {
        isBlockScroll -> mainAxisSizePx.toFloat()
        hostOs == OS.Linux -> sqrt(mainAxisSizePx.toFloat())
        hostOs == OS.MacOS -> with(density) { MAC_OS_WHEEL_DELTA.toPx() }
        else -> mainAxisSizePx / WINDOWS_WHEEL_BOUNDS_DIVISOR
    }

/**
 * `true` when [crossAxisPositionPx] falls in the lane reserved by [Modifier.scrollIndicatorReservation].
 *
 * The lane sits at the end of the cross axis, except for a vertical indicator in RTL, where it is at the start.
 */
internal fun isInReservedLane(
    crossAxisPositionPx: Float,
    crossAxisSizePx: Int,
    laneThicknessPx: Float,
    laneAtStart: Boolean,
): Boolean =
    if (laneAtStart) {
        crossAxisPositionPx in 0f..laneThicknessPx
    } else {
        // Clamp the start so an oversized lane cannot swallow the whole cross axis by going negative.
        crossAxisPositionPx in (crossAxisSizePx - laneThicknessPx).coerceAtLeast(0f)..crossAxisSizePx.toFloat()
    }

private val MAC_OS_WHEEL_DELTA = 10.dp
private const val WINDOWS_WHEEL_BOUNDS_DIVISOR = 20f
