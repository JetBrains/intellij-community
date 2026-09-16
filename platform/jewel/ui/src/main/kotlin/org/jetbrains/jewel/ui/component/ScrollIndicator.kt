// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jewel.ui.component

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ScrollIndicatorState
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.ScrollableState
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitHorizontalDragOrCancellation
import androidx.compose.foundation.gestures.awaitVerticalDragOrCancellation
import androidx.compose.foundation.gestures.horizontalDrag
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.gestures.verticalDrag
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableFloatState
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.awt.awtEventOrNull
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.AwaitPointerEventScope
import androidx.compose.ui.input.pointer.PointerEvent
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerId
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.isPrimaryPressed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.node.DrawModifierNode
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.node.invalidateDraw
import androidx.compose.ui.platform.InspectorInfo
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.toSize
import java.awt.event.MouseWheelEvent
import kotlin.math.roundToInt
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.jewel.foundation.ExperimentalJewelApi
import org.jetbrains.jewel.foundation.modifier.thenIf
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.component.styling.ScrollbarStyle
import org.jetbrains.jewel.ui.component.styling.ScrollbarVisibility
import org.jetbrains.jewel.ui.component.styling.ScrollbarVisibility.AlwaysVisible
import org.jetbrains.jewel.ui.component.styling.TrackClickBehavior
import org.jetbrains.jewel.ui.theme.scrollbarStyle
import org.jetbrains.skiko.OS
import org.jetbrains.skiko.hostOs

/**
 * Draws a Jewel-styled scroll indicator over the content of the modified layout node, and handles thumb drags and track
 * clicks on it.
 *
 * Unlike [VerticalScrollbar], which is a composable laid out next to (or over) the content, this modifier paints the
 * indicator over the content it is applied to. Use it when you want a scrollbar on existing content without wrapping it
 * in another container — for example, over a lazy layout you already own the scrolling of.
 *
 * Layout space is reserved only where the platform scrollbar is not an overlay: on macOS with
 * [ScrollbarVisibility.AlwaysVisible] the indicator gets its own lane beside the content, exactly as
 * [VerticallyScrollableContainer] does. In every other case it floats over the content and the layout is untouched.
 *
 * When it floats, part of your content sits under the indicator. Use [scrollbarContentSafePadding] to find out how much
 * room to keep clear, and apply it yourself to the elements that must not be overlapped — text, buttons, and the like.
 * It is deliberately not applied for you: decoration such as dividers or backgrounds is usually fine underneath, and
 * only you know which is which.
 *
 * The indicator reads its scroll metrics from [ScrollableState.scrollIndicatorState]. When that is `null`, or while the
 * content is not scrollable, nothing is drawn and no pointer events are consumed.
 *
 * It is revealed by scrolling, by dragging it, or by hovering its track, and hides again after
 * [ScrollbarVisibility.lingerDuration]. While it is visible, moving the pointer over the content re-arms that timeout,
 * matching [VerticallyScrollableContainer]. It is drawn at the end of the layout: a vertical indicator sits on the
 * right edge in LTR and the left edge in RTL, and a horizontal one along the bottom edge.
 *
 * This is an opt-in Jewel component. It is not a replacement for [VerticalScrollbar] or
 * [VerticallyScrollableContainer], and it does not implement the withdrawn `Modifier.scrollIndicator` factory API from
 * Compose Foundation; it only consumes the stable [ScrollIndicatorState] contract from it.
 *
 * **Guidelines:** [on IJP SDK webhelp](https://plugins.jetbrains.com/docs/intellij/scrollbar.html)
 *
 * **Usage example:**
 * [`Scrollbars.kt`](https://github.com/JetBrains/intellij-community/blob/master/platform/jewel/samples/showcase/src/main/kotlin/org/jetbrains/jewel/samples/showcase/components/Scrollbars.kt)
 *
 * @param scrollState The [ScrollableState] the indicator reflects and controls.
 * @param orientation The scroll axis the indicator represents.
 * @param style The [ScrollbarStyle] to use for this indicator.
 * @param reverseLayout `true` to reverse the direction of the indicator, `false` otherwise.
 * @param enabled `true` to enable interacting with the indicator, `false` otherwise. When disabled the indicator is
 *   fully inert, like a disabled Swing scrollbar: it still draws, but it does not expand on hover, take clicks or
 *   drags, or forward the wheel over its reserved lane. The content itself keeps scrolling.
 * @param keepVisible `true` to keep the indicator visible indefinitely, `false` to let it hide after
 *   [ScrollbarVisibility.lingerDuration]. Has no effect under [ScrollbarVisibility.AlwaysVisible], which never hides.
 */
@Composable
@ExperimentalJewelApi
@ApiStatus.Experimental
public fun Modifier.scrollIndicator(
    scrollState: ScrollableState,
    orientation: Orientation = Orientation.Vertical,
    style: ScrollbarStyle = JewelTheme.scrollbarStyle,
    reverseLayout: Boolean = false,
    enabled: Boolean = true,
    keepVisible: Boolean = false,
): Modifier {
    // Read through state so that a style or flag change at runtime (e.g. a LaF switch) is picked up
    // by the derived states below, instead of being frozen at first composition.
    val visibility by rememberUpdatedState(style.scrollbarVisibility)
    val currentKeepVisible by rememberUpdatedState(keepVisible)
    val isOpaque by remember { derivedStateOf { visibility is AlwaysVisible } }

    var isHovered by remember { mutableStateOf(false) }
    var isDragging by remember { mutableStateOf(false) }
    var showIndicator by remember { mutableStateOf(style.scrollbarVisibility is AlwaysVisible) }

    // Set while the pointer has recently moved over the content, and cleared once it has been still
    // for the linger duration. Like VerticallyScrollableContainer's keep-visible latch, this only
    // *prolongs* an already-visible indicator; a move never reveals a hidden one.
    var movedRecently by remember { mutableStateOf(false) }
    var moveTick by remember { mutableLongStateOf(0) }

    val isScrolling by remember(scrollState) { derivedStateOf { scrollState.isScrollInProgress || isDragging } }
    val isExpanded by remember { derivedStateOf { showIndicator && (isHovered || isDragging) } }
    val isActive by remember {
        derivedStateOf { isOpaque || isScrolling || ((movedRecently || currentKeepVisible) && showIndicator) }
    }

    // Each move restarts this effect, so the latch only clears once the pointer has been still for
    // the whole linger duration.
    LaunchedEffect(moveTick) {
        if (moveTick == 0L) return@LaunchedEffect
        delay(visibility.lingerDuration)
        movedRecently = false
    }

    // Mirrors BaseScrollbar: reveal immediately while active or hovered, and linger before hiding again.
    LaunchedEffect(isActive, isHovered, isDragging) {
        if (isActive || isHovered) {
            showIndicator = true
        } else if (!isDragging) {
            delay(visibility.lingerDuration)
            showIndicator = false
        }
    }

    val animatedThickness by
        animateDpAsState(
            targetValue = if (isExpanded) visibility.trackThicknessExpanded else visibility.trackThickness,
            // The delay is what stops a pointer crossing the track from making it flicker open, as in Swing.
            animationSpec =
                tween(
                    durationMillis = visibility.expandAnimationDuration.inWholeMilliseconds.toInt(),
                    delayMillis =
                        if (isExpanded) {
                            visibility.expandDelay.inWholeMilliseconds.toInt()
                        } else {
                            visibility.collapseDelay.inWholeMilliseconds.toInt()
                        },
                    easing = LinearEasing,
                ),
            label = "scrollIndicator_thickness",
        )

    val thumbBackgroundTarget = thumbBackgroundColor(style, isOpaque, isHovered, isScrolling, showIndicator)
    val thumbBorderTarget = thumbBorderColor(style, isOpaque, isHovered, isScrolling, showIndicator)
    val hasVisibleBorder = !areTheSameColor(thumbBackgroundTarget, thumbBorderTarget)

    val thumbBackground by
        animateColorAsState(
            targetValue = thumbBackgroundTarget,
            animationSpec = thumbColorTween(showIndicator, visibility),
            label = "scrollIndicator_thumbBackground",
        )
    val thumbBorder by
        animateColorAsState(
            targetValue = thumbBorderTarget,
            animationSpec = thumbColorTween(showIndicator, visibility),
            label = "scrollIndicator_thumbBorder",
        )
    val trackBackground by
        animateColorAsState(
            targetValue = trackBackgroundColor(style, isOpaque, isHovered, isDragging, isExpanded),
            animationSpec =
                tween(visibility.trackColorAnimationDuration.inWholeMilliseconds.toInt(), easing = LinearEasing),
            label = "scrollIndicator_trackBackground",
        )

    val trackPadding =
        when {
            isExpanded -> visibility.trackPaddingExpanded
            hasVisibleBorder -> visibility.trackPaddingWithBorder
            else -> visibility.trackPadding
        }

    val metrics =
        ScrollIndicatorMetrics(
            thickness = animatedThickness,
            trackPadding = trackPadding,
            minThumbLength = style.metrics.minThumbLength,
            reverseLayout = reverseLayout,
            orientation = orientation,
        )
    val currentMetrics = rememberUpdatedState(metrics)
    val isVisible = showIndicator || isOpaque
    val currentIsVisible = rememberUpdatedState(isVisible)
    val currentIsExpanded = rememberUpdatedState(isExpanded)
    val currentIsOpaque = rememberUpdatedState(isOpaque)
    val scope = rememberCoroutineScope()
    val layoutDirection = LocalLayoutDirection.current

    val reservedThickness = scrollIndicatorReservedThickness(style, hostOs == OS.MacOS)

    // Resolved last so that every remembered value above is read unconditionally, keeping the
    // composition structure stable if the scrollable starts or stops exposing indicator metrics.
    val indicatorState =
        scrollState.scrollIndicatorState ?: return scrollIndicatorReservation(reservedThickness, orientation)

    // No semantics modifier here: unlike VerticalScrollbar, which hides its own layout node, this
    // modifier decorates the caller's content node, and hiding that would hide the content too.
    return this.then(
            ScrollIndicatorElement(
                state = indicatorState,
                metrics = metrics,
                thumbBackground = thumbBackground,
                thumbBorder = thumbBorder,
                trackBackground = trackBackground,
                hasVisibleBorder = hasVisibleBorder,
                thumbCornerRadius = style.metrics.thumbCornerSize,
                isVisible = isVisible,
                isExpanded = isExpanded,
            )
        )
        .thenIf(enabled) {
            hoverTracking(
                metrics = currentMetrics,
                layoutDirection = layoutDirection,
                onPointerMove = {
                    // Prolong only: moving over hidden content must not reveal the indicator.
                    if (showIndicator) {
                        movedRecently = true
                        moveTick++
                    }
                },
                onHoverChange = { isHovered = it },
            )
        }
        .thenIf(enabled) {
            trackClicks(
                    state = indicatorState,
                    scrollState = scrollState,
                    metrics = currentMetrics,
                    isVisible = currentIsVisible,
                    isExpanded = currentIsExpanded,
                    isOpaque = currentIsOpaque,
                    clickBehavior = style.trackClickBehavior,
                    layoutDirection = layoutDirection,
                    scope = scope,
                )
                .thumbDrag(indicatorState, scrollState, currentMetrics, currentIsVisible, layoutDirection) {
                    isDragging = it
                }
        }
        // Above the reservation, so it still sees the full node and can spot the reserved lane.
        .thenIf(enabled) {
            reservedLaneWheel(scrollState, reservedThickness, orientation, layoutDirection, reverseLayout)
        }
        // Innermost, so the draw and pointer nodes above still measure the full node and own the
        // reserved lane; only the content below is narrowed.
        .scrollIndicatorReservation(reservedThickness, orientation)
}

/**
 * Forwards wheel events over the reserved lane to [scrollState].
 *
 * [Modifier.scrollIndicatorReservation] measures the content narrower than the node, so the lane is outside the
 * scrollable child and the wheel would otherwise do nothing there. The Swing scrollbar scrolls in this case.
 */
private fun Modifier.reservedLaneWheel(
    scrollState: ScrollableState,
    laneThickness: Dp,
    orientation: Orientation,
    layoutDirection: LayoutDirection,
    reverseLayout: Boolean,
): Modifier {
    if (laneThickness <= 0.dp) return this

    return pointerInput(laneThickness, orientation, layoutDirection, reverseLayout, scrollState) {
        val laneThicknessPx = laneThickness.toPx()
        val isVertical = orientation == Orientation.Vertical
        // The reservation puts the lane on the left for a vertical indicator in RTL.
        val laneAtStart = isVertical && layoutDirection == LayoutDirection.Rtl
        awaitPointerEventScope {
            while (true) {
                val event = awaitPointerEvent()
                val rawDelta =
                    event.reservedLaneScrollDelta(
                        size = size,
                        laneThicknessPx = laneThicknessPx,
                        isVertical = isVertical,
                        laneAtStart = laneAtStart,
                        density = this,
                    )
                if (rawDelta != null) {
                    // ScrollableDefaults.reverseDirection flips again for a horizontal scrollable in RTL, so the
                    // lane has to match or the wheel would scroll the opposite way to the content beside it.
                    val mirrored = reverseLayout != (!isVertical && layoutDirection == LayoutDirection.Rtl)
                    val consumed = scrollState.dispatchRawDelta(if (mirrored) -rawDelta else rawDelta)
                    if (consumed != 0f) event.changes.firstOrNull()?.consume()
                }
            }
        }
    }
}

/**
 * The scroll distance, in pixels, for a wheel event over the reserved lane, or `null` when the event is not a wheel
 * scroll over that lane.
 */
private fun PointerEvent.reservedLaneScrollDelta(
    size: IntSize,
    laneThicknessPx: Float,
    isVertical: Boolean,
    laneAtStart: Boolean,
    density: Density,
): Float? {
    if (type != PointerEventType.Scroll) return null

    val change = changes.firstOrNull() ?: return null
    val crossAxisPositionPx = if (isVertical) change.position.x else change.position.y
    val crossAxisSizePx = if (isVertical) size.width else size.height
    if (!isInReservedLane(crossAxisPositionPx, crossAxisSizePx, laneThicknessPx, laneAtStart)) return null

    val wheelDelta =
        changes.fold(0f) { total, pointerChange ->
            val delta = pointerChange.scrollDelta
            total + if (isVertical || delta.x == 0f) delta.y else delta.x
        }
    if (wheelDelta == 0f) return null

    val awtEvent = awtEventOrNull as? MouseWheelEvent
    val deltaToPixels =
        scrollIndicatorWheelDeltaToPixels(
            mainAxisSizePx = if (isVertical) size.height else size.width,
            isBlockScroll = awtEvent?.scrollType == MouseWheelEvent.WHEEL_BLOCK_SCROLL,
            density = density,
        )
    return wheelDelta * deltaToPixels * (awtEvent?.scrollAmount?.toFloat() ?: 1f)
}

/** The resolved, animation-aware sizing used by both drawing and pointer hit testing. */
internal data class ScrollIndicatorMetrics(
    val thickness: Dp,
    val trackPadding: PaddingValues,
    val minThumbLength: Dp,
    val reverseLayout: Boolean,
    val orientation: Orientation,
)

private class ScrollIndicatorElement(
    private val state: ScrollIndicatorState,
    private val metrics: ScrollIndicatorMetrics,
    private val thumbBackground: Color,
    private val thumbBorder: Color,
    private val trackBackground: Color,
    private val hasVisibleBorder: Boolean,
    private val thumbCornerRadius: CornerSize,
    private val isVisible: Boolean,
    private val isExpanded: Boolean,
) : ModifierNodeElement<ScrollIndicatorNode>() {
    override fun create(): ScrollIndicatorNode =
        ScrollIndicatorNode(
            state,
            metrics,
            thumbBackground,
            thumbBorder,
            trackBackground,
            hasVisibleBorder,
            thumbCornerRadius,
            isVisible,
            isExpanded,
        )

    override fun update(node: ScrollIndicatorNode) {
        node.update(
            state,
            metrics,
            thumbBackground,
            thumbBorder,
            trackBackground,
            hasVisibleBorder,
            thumbCornerRadius,
            isVisible,
            isExpanded,
        )
    }

    override fun InspectorInfo.inspectableProperties() {
        name = "scrollIndicator"
        properties["state"] = state
        properties["metrics"] = metrics
        properties["isVisible"] = isVisible
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is ScrollIndicatorElement) return false

        return state == other.state &&
            metrics == other.metrics &&
            thumbBackground == other.thumbBackground &&
            thumbBorder == other.thumbBorder &&
            trackBackground == other.trackBackground &&
            hasVisibleBorder == other.hasVisibleBorder &&
            thumbCornerRadius == other.thumbCornerRadius &&
            isVisible == other.isVisible &&
            isExpanded == other.isExpanded
    }

    override fun hashCode(): Int {
        var result = state.hashCode()
        result = 31 * result + metrics.hashCode()
        result = 31 * result + thumbBackground.hashCode()
        result = 31 * result + thumbBorder.hashCode()
        result = 31 * result + trackBackground.hashCode()
        result = 31 * result + hasVisibleBorder.hashCode()
        result = 31 * result + thumbCornerRadius.hashCode()
        result = 31 * result + isVisible.hashCode()
        result = 31 * result + isExpanded.hashCode()
        return result
    }
}

private class ScrollIndicatorNode(
    private var state: ScrollIndicatorState,
    private var metrics: ScrollIndicatorMetrics,
    private var thumbBackground: Color,
    private var thumbBorder: Color,
    private var trackBackground: Color,
    private var hasVisibleBorder: Boolean,
    private var thumbCornerRadius: CornerSize,
    private var isVisible: Boolean,
    private var isExpanded: Boolean,
) : Modifier.Node(), DrawModifierNode {
    fun update(
        state: ScrollIndicatorState,
        metrics: ScrollIndicatorMetrics,
        thumbBackground: Color,
        thumbBorder: Color,
        trackBackground: Color,
        hasVisibleBorder: Boolean,
        thumbCornerRadius: CornerSize,
        isVisible: Boolean,
        isExpanded: Boolean,
    ) {
        this.state = state
        this.metrics = metrics
        this.thumbBackground = thumbBackground
        this.thumbBorder = thumbBorder
        this.trackBackground = trackBackground
        this.hasVisibleBorder = hasVisibleBorder
        this.thumbCornerRadius = thumbCornerRadius
        this.isVisible = isVisible
        this.isExpanded = isExpanded
        invalidateDraw()
    }

    override fun ContentDrawScope.draw() {
        // Draw the content first, so the indicator paints on top of it rather than underneath.
        drawContent()
        if (!isVisible || !state.canScroll) return

        val track = trackBounds(metrics, layoutDirection) ?: return
        val minThumbLengthPx = metrics.minThumbLength.toPx().roundToInt()
        val thumb = state.thumbGeometry(track, minThumbLengthPx)
        if (thumb.sizePx <= 0) return

        // BaseScrollbar only paints the track background while expanded, so custom non-transparent
        // track colours must not bleed through in the collapsed state.
        if (isExpanded) {
            drawRect(color = trackBackground, topLeft = track.axis.bounds.topLeft, size = track.axis.bounds.size)
        }

        val borderWidthPx = if (hasVisibleBorder) 1.dp.toPx() else 0f
        val thumbRect =
            track.axis.thumbRect(
                offsetPx = thumb.offsetPx.toFloat(),
                lengthPx = thumb.sizePx.toFloat(),
                startInsetPx = track.crossStartPx,
                endInsetPx = track.crossEndPx,
            )
        val cornerRadiusPx = thumbCornerRadius.toPx(thumbRect.size, this)

        // Inset the fill by the border width so the stroke is drawn around it, not over it.
        val fillCornerRadius = (cornerRadiusPx - borderWidthPx * 2).coerceAtLeast(0f)
        drawRoundRect(
            color = thumbBackground,
            topLeft = Offset(thumbRect.left + borderWidthPx, thumbRect.top + borderWidthPx),
            size = Size(thumbRect.width - borderWidthPx * 2, thumbRect.height - borderWidthPx * 2),
            cornerRadius = CornerRadius(fillCornerRadius),
        )

        if (hasVisibleBorder) {
            drawRoundRect(
                color = thumbBorder,
                topLeft = Offset(thumbRect.left + borderWidthPx / 2, thumbRect.top + borderWidthPx / 2),
                size = Size(thumbRect.width - borderWidthPx, thumbRect.height - borderWidthPx),
                cornerRadius = CornerRadius(cornerRadiusPx),
                style = Stroke(borderWidthPx),
            )
        }
    }
}

/** The padded track the thumb travels along, resolved for the indicator's orientation. */
private data class TrackBounds(
    val axis: ScrollIndicatorAxisGeometry,
    val startInsetPx: Int,
    val endInsetPx: Int,
    val crossStartPx: Float,
    val crossEndPx: Float,
    val crossThicknessPx: Float,
) {
    val lengthPx: Int
        get() = axis.trackLengthPx
}

private fun Density.trackBounds(
    metrics: ScrollIndicatorMetrics,
    layoutDirection: LayoutDirection,
    size: Size,
): TrackBounds? {
    val thicknessPx = metrics.thickness.toPx()
    val axis = scrollIndicatorAxisGeometry(size, thicknessPx, metrics.orientation, layoutDirection)
    val padding = metrics.trackPadding
    val crossStartPx = padding.crossAxisStartPadding(metrics.orientation, layoutDirection).toPx()
    val crossEndPx = padding.crossAxisEndPadding(metrics.orientation, layoutDirection).toPx()
    val crossThicknessPx = thicknessPx - crossStartPx - crossEndPx
    if (crossThicknessPx <= 0f || axis.trackLengthPx <= 0) return null

    return TrackBounds(
        axis = axis,
        startInsetPx = padding.mainAxisStartPadding(metrics.orientation, layoutDirection).toPx().roundToInt(),
        endInsetPx = padding.mainAxisEndPadding(metrics.orientation, layoutDirection).toPx().roundToInt(),
        crossStartPx = crossStartPx,
        crossEndPx = crossEndPx,
        crossThicknessPx = crossThicknessPx,
    )
}

private fun ContentDrawScope.trackBounds(metrics: ScrollIndicatorMetrics, layoutDirection: LayoutDirection) =
    trackBounds(metrics, layoutDirection, size)

private fun PointerInputScope.trackBounds(metrics: ScrollIndicatorMetrics, layoutDirection: LayoutDirection) =
    trackBounds(metrics, layoutDirection, size.toSize())

/** `true` when the metrics are known and the content is actually scrollable. */
private val ScrollIndicatorState.canScroll: Boolean
    get() =
        viewportSize > 0 && viewportSize != Int.MAX_VALUE && contentSize != Int.MAX_VALUE && contentSize > viewportSize

private fun ScrollIndicatorState.thumbGeometry(
    track: TrackBounds,
    minThumbLengthPx: Int,
): ScrollIndicatorThumbGeometry =
    ScrollIndicatorGeometry(contentSize, viewportSize, scrollOffset)
        .thumbGeometry(
            trackLengthPx = track.lengthPx,
            startInsetPx = track.startInsetPx,
            endInsetPx = track.endInsetPx,
            minThumbLengthPx = minThumbLengthPx,
        )

/**
 * Tracks whether the pointer is over the track, and separately whether it has moved at all over the content.
 *
 * The latter drives the keep-visible latch: [onPointerMove] is called for every movement, not just movements over the
 * track.
 */
private fun Modifier.hoverTracking(
    metrics: State<ScrollIndicatorMetrics>,
    layoutDirection: LayoutDirection,
    onPointerMove: () -> Unit,
    onHoverChange: (Boolean) -> Unit,
): Modifier =
    pointerInput(layoutDirection) {
        awaitPointerEventScope {
            while (true) {
                val event = awaitPointerEvent()
                when (event.type) {
                    PointerEventType.Exit -> onHoverChange(false)
                    PointerEventType.Move,
                    PointerEventType.Enter -> {
                        onPointerMove()
                        val position = event.changes.lastOrNull()?.position ?: continue
                        val track = trackBounds(metrics.value, layoutDirection)
                        onHoverChange(track != null && track.axis.contains(position))
                    }
                }
            }
        }
    }

private fun Modifier.trackClicks(
    state: ScrollIndicatorState,
    scrollState: ScrollableState,
    metrics: State<ScrollIndicatorMetrics>,
    isVisible: State<Boolean>,
    isExpanded: State<Boolean>,
    isOpaque: State<Boolean>,
    clickBehavior: TrackClickBehavior,
    layoutDirection: LayoutDirection,
    scope: CoroutineScope,
): Modifier =
    pointerInput(state, scrollState, layoutDirection, clickBehavior) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = true)
            // A hidden indicator must not swallow clicks meant for the content underneath. Only the
            // primary button scrolls: the Swing scrollbar also treats a middle click as an absolute
            // jump (`DefaultScrollBarUI.isAbsolutePositioning`), which this modifier does not do.
            if (!isVisible.value || !state.canScroll || !currentEvent.buttons.isPrimaryPressed) {
                return@awaitEachGesture
            }
            // A collapsed overlay track passes clicks through to the content: Swing's
            // `isTrackClickable()` needs `isOpaque || trackFrame > 0`, so a thumb revealed by scrolling
            // alone is not clickable until the track has expanded under the pointer.
            if (!isExpanded.value && !isOpaque.value) return@awaitEachGesture

            val currentMetrics = metrics.value
            val track = trackBounds(currentMetrics, layoutDirection) ?: return@awaitEachGesture
            if (!track.axis.contains(down.position)) return@awaitEachGesture

            val minThumbLengthPx = currentMetrics.minThumbLength.toPx().roundToInt()
            val thumb = state.thumbGeometry(track, minThumbLengthPx)
            val pressOffsetPx = track.axis.mainAxisOffset(down.position)
            // Leave presses on the thumb to the drag handler.
            if (thumb.contains(pressOffsetPx)) return@awaitEachGesture
            down.consume()

            val pressOffset = mutableFloatStateOf(pressOffsetPx)
            val pressed = mutableStateOf(true)
            val pressJob = mutableStateOf<Job?>(null)

            // The initial scroll runs on the composition scope, so releasing the press cannot cancel
            // it. It also latches the paging direction for the repeat loop below.
            scope.launch {
                val pagingDirection =
                    scrollTowards(
                        state = state,
                        scrollState = scrollState,
                        track = track,
                        metrics = currentMetrics,
                        minThumbLengthPx = minThumbLengthPx,
                        pressOffsetPx = pressOffsetPx,
                        clickBehavior = clickBehavior,
                    )

                pressJob.value =
                    scope.launch {
                        scrollWhilePressed(
                            state = state,
                            scrollState = scrollState,
                            track = track,
                            metrics = currentMetrics,
                            minThumbLengthPx = minThumbLengthPx,
                            pressOffsetPx = pressOffset,
                            clickBehavior = clickBehavior,
                            pagingDirection = pagingDirection,
                        )
                    }
                if (!pressed.value) pressJob.value?.cancel()
            }

            try {
                while (true) {
                    val change = awaitDragOrCancellation(down.id, currentMetrics.orientation) ?: break
                    if (!change.pressed) break
                    change.consume()
                    pressOffset.floatValue = track.axis.mainAxisOffset(change.position)
                }
            } finally {
                // Stop following the press once it ends; any scroll it already started still runs.
                pressed.value = false
                pressJob.value?.cancel()
            }
        }
    }

/**
 * Keeps scrolling towards the pressed spot on the track while the press is held.
 *
 * The initial scroll is performed by the caller. [TrackClickBehavior.JumpToSpot] then follows the pointer as it moves,
 * while [TrackClickBehavior.NextPage] keeps paging with the same delays the platform scrollbar uses.
 */
private suspend fun scrollWhilePressed(
    state: ScrollIndicatorState,
    scrollState: ScrollableState,
    track: TrackBounds,
    metrics: ScrollIndicatorMetrics,
    minThumbLengthPx: Int,
    pressOffsetPx: MutableFloatState,
    clickBehavior: TrackClickBehavior,
    pagingDirection: Int,
) {
    when (clickBehavior) {
        // snapshotFlow suspends until the press offset actually moves, so a stationary press adds
        // nothing on top of the caller's initial jump.
        TrackClickBehavior.JumpToSpot ->
            snapshotFlow { pressOffsetPx.floatValue }
                .drop(1)
                .collectLatest { offsetPx ->
                    scrollTowards(state, scrollState, track, metrics, minThumbLengthPx, offsetPx, clickBehavior)
                }

        TrackClickBehavior.NextPage -> {
            if (pagingDirection == 0) return
            delay(DELAY_BEFORE_SECOND_SCROLL_ON_TRACK_PRESS)
            while (true) {
                val scrolled =
                    scrollTowards(
                        state,
                        scrollState,
                        track,
                        metrics,
                        minThumbLengthPx,
                        pressOffsetPx.floatValue,
                        clickBehavior,
                        pagingDirection,
                    )
                if (scrolled == 0) {
                    // The thumb has reached the press, or the content is at its limit. Idle instead of
                    // ticking, but stay ready: the Swing scrollbar restarts its repeat timer on every
                    // drag (`DefaultScrollBarUI.mouseDragged` -> `startScrollTimerIfNecessary`), so
                    // dragging further along the track has to resume paging.
                    val restingOffset = pressOffsetPx.floatValue
                    snapshotFlow { pressOffsetPx.floatValue }.first { it != restingOffset }
                    continue
                }
                delay(DELAY_BETWEEN_SCROLLS_ON_TRACK_PRESS)
            }
        }
    }
}

/**
 * Scrolls once towards [pressOffsetPx] on the track.
 *
 * Scrolling is instant rather than animated, matching [VerticalScrollbar]: an animation would still be running when the
 * next paging step is due, and the two would cancel each other out.
 *
 * For [TrackClickBehavior.NextPage], [pagingDirection] latches the direction the gesture started in; the step is
 * skipped once the thumb has caught up with the pointer, so a press that overshoots does not oscillate. Returns the
 * direction that was applied, or 0 when nothing was scrolled.
 */
private suspend fun scrollTowards(
    state: ScrollIndicatorState,
    scrollState: ScrollableState,
    track: TrackBounds,
    metrics: ScrollIndicatorMetrics,
    minThumbLengthPx: Int,
    pressOffsetPx: Float,
    clickBehavior: TrackClickBehavior,
    pagingDirection: Int = 0,
): Int {
    val maxScrollOffsetPx = (state.contentSize - state.viewportSize).coerceAtLeast(0)
    if (track.lengthPx <= 0 || maxScrollOffsetPx <= 0) return 0

    val thumb = state.thumbGeometry(track, minThumbLengthPx)
    when (clickBehavior) {
        TrackClickBehavior.JumpToSpot -> {
            val target =
                targetScrollOffsetForThumbCenter(
                    pointerOffsetPx = pressOffsetPx - thumb.trackStartPx,
                    trackLengthPx = thumb.trackLengthPx,
                    thumbSizePx = thumb.sizePx,
                    maxScrollOffsetPx = maxScrollOffsetPx,
                )
            val delta = target - state.scrollOffset
            scrollState.scrollBy(if (metrics.reverseLayout) -delta else delta)
            return 0
        }

        TrackClickBehavior.NextPage -> {
            val direction = pageDirectionTowardPointer(pressOffsetPx, thumb.offsetPx, thumb.sizePx)
            if (direction == 0) return 0
            if (pagesBackPastPress(pagingDirection, direction)) return 0

            val pageDelta = direction * state.viewportSize.toFloat()
            val consumed = scrollState.scrollBy(if (metrics.reverseLayout) -pageDelta else pageDelta)
            // At the scroll limit nothing moves, so report that no paging happened.
            return if (consumed != 0f) direction else 0
        }
    }
}

private fun Modifier.thumbDrag(
    state: ScrollIndicatorState,
    scrollState: ScrollableState,
    metrics: State<ScrollIndicatorMetrics>,
    isVisible: State<Boolean>,
    layoutDirection: LayoutDirection,
    onDraggingChange: (Boolean) -> Unit,
): Modifier =
    pointerInput(state, scrollState, layoutDirection) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false)
            if (!isVisible.value || !state.canScroll || !currentEvent.buttons.isPrimaryPressed) {
                return@awaitEachGesture
            }

            val currentMetrics = metrics.value
            val track = trackBounds(currentMetrics, layoutDirection) ?: return@awaitEachGesture
            val minThumbLengthPx = currentMetrics.minThumbLength.toPx().roundToInt()
            val thumb = state.thumbGeometry(track, minThumbLengthPx)
            if (!track.axis.contains(down.position) || !thumb.contains(track.axis.mainAxisOffset(down.position))) {
                return@awaitEachGesture
            }

            // Where inside the thumb the drag started. The Swing scrollbar keeps that point under the
            // pointer for the whole drag (`DefaultScrollBarUI.setValueFrom` clamps `y - myOffset`), so
            // grabbing an edge must not snap the thumb's centre to the cursor.
            val grabOffsetPx = track.axis.mainAxisOffset(down.position) - thumb.offsetPx

            onDraggingChange(true)
            try {
                awaitDrag(down.id, currentMetrics.orientation) { change ->
                    change.consume()
                    // Absolute positioning: each event maps the pointer onto the scroll range, so
                    // overshooting an end and coming back cannot drift.
                    moveThumbToPointer(
                        pointerOffsetPx = track.axis.mainAxisOffset(change.position),
                        grabOffsetPx = grabOffsetPx,
                        track = track,
                        state = state,
                        scrollState = scrollState,
                        metrics = currentMetrics,
                        minThumbLengthPx = minThumbLengthPx,
                    )
                }
            } finally {
                onDraggingChange(false)
            }
        }
    }

/** Scrolls so that the point [grabOffsetPx] into the thumb lands on [pointerOffsetPx]. */
private fun moveThumbToPointer(
    pointerOffsetPx: Float,
    grabOffsetPx: Float,
    track: TrackBounds,
    state: ScrollIndicatorState,
    scrollState: ScrollableState,
    metrics: ScrollIndicatorMetrics,
    minThumbLengthPx: Int,
) {
    val maxScrollOffsetPx = (state.contentSize - state.viewportSize).coerceAtLeast(0)
    if (maxScrollOffsetPx <= 0) return

    val thumb = state.thumbGeometry(track, minThumbLengthPx)
    val target =
        targetScrollOffsetForThumbStart(
            thumbStartPx = pointerOffsetPx - grabOffsetPx - thumb.trackStartPx,
            trackLengthPx = thumb.trackLengthPx,
            thumbSizePx = thumb.sizePx,
            maxScrollOffsetPx = maxScrollOffsetPx,
        )
    val visualDeltaPx = target - state.scrollOffset
    val deltaPx = if (metrics.reverseLayout) -visualDeltaPx else visualDeltaPx
    // dispatchRawDelta is synchronous, so a fast drag cannot queue up competing animations.
    if (deltaPx != 0f) scrollState.dispatchRawDelta(deltaPx)
}

private suspend fun AwaitPointerEventScope.awaitDragOrCancellation(
    pointerId: PointerId,
    orientation: Orientation,
): PointerInputChange? =
    if (orientation == Orientation.Vertical) {
        awaitVerticalDragOrCancellation(pointerId)
    } else {
        awaitHorizontalDragOrCancellation(pointerId)
    }

private suspend fun AwaitPointerEventScope.awaitDrag(
    pointerId: PointerId,
    orientation: Orientation,
    onDrag: (PointerInputChange) -> Unit,
) {
    if (orientation == Orientation.Vertical) verticalDrag(pointerId, onDrag) else horizontalDrag(pointerId, onDrag)
}

private fun trackBackgroundColor(
    style: ScrollbarStyle,
    isOpaque: Boolean,
    isHovered: Boolean,
    isDragging: Boolean,
    isExpanded: Boolean,
): Color =
    if (isOpaque) {
        if (isHovered || isDragging) style.colors.trackOpaqueBackgroundHovered else style.colors.trackOpaqueBackground
    } else {
        if (isExpanded) style.colors.trackBackgroundExpanded else style.colors.trackBackground
    }

private fun thumbBackgroundColor(
    style: ScrollbarStyle,
    isOpaque: Boolean,
    isHovered: Boolean,
    isScrolling: Boolean,
    showIndicator: Boolean,
): Color =
    if (isOpaque) {
        if (isHovered || isScrolling) style.colors.thumbOpaqueBackgroundHovered else style.colors.thumbOpaqueBackground
    } else {
        if (showIndicator) style.colors.thumbBackgroundActive else style.colors.thumbBackground
    }

private fun thumbBorderColor(
    style: ScrollbarStyle,
    isOpaque: Boolean,
    isHovered: Boolean,
    isScrolling: Boolean,
    showIndicator: Boolean,
): Color =
    if (isOpaque) {
        if (isHovered || isScrolling) style.colors.thumbOpaqueBorderHovered else style.colors.thumbOpaqueBorder
    } else {
        if (showIndicator) style.colors.thumbBorderActive else style.colors.thumbBorder
    }

private fun areTheSameColor(first: Color, second: Color) = first.toArgb() == second.toArgb()

private fun thumbColorTween(showIndicator: Boolean, visibility: ScrollbarVisibility) =
    tween<Color>(
        durationMillis =
            when {
                visibility is AlwaysVisible || !showIndicator ->
                    visibility.thumbColorAnimationDuration.inWholeMilliseconds.toInt()

                else -> 0
            },
        delayMillis =
            if (visibility is AlwaysVisible && !showIndicator) visibility.lingerDuration.inWholeMilliseconds.toInt()
            else 0,
        easing = LinearEasing,
    )
