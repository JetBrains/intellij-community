// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jewel.ui.component

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.MutatePriority
import androidx.compose.foundation.ScrollIndicatorState
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.ScrollScope
import androidx.compose.foundation.gestures.ScrollableState
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableFloatState
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
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
import androidx.compose.ui.input.pointer.PointerEvent
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.isPrimaryPressed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChangeIgnoreConsumed
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
import kotlin.time.Duration
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
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
    val visibility = style.scrollbarVisibility
    val isOpaque = visibility is AlwaysVisible

    var isHovered by remember { mutableStateOf(false) }
    var isDragging by remember { mutableStateOf(false) }
    var isTrackPressed by remember { mutableStateOf(false) }
    var showIndicator by remember { mutableStateOf(isOpaque) }
    var isExpanded by remember { mutableStateOf(false) }

    // Conflated channel used to re-arm the linger timeout on pointer moves over the content without
    // writing to Compose state (and recomposing the caller) on every mouse movement pixel.
    val pointerMoveChannel = remember { Channel<Unit>(Channel.CONFLATED) }

    val isScrolling = scrollState.isScrollInProgress || isDragging || isTrackPressed
    val shouldHoldVisible = isOpaque || isScrolling || (enabled && isHovered) || (keepVisible && showIndicator)
    val effectiveShowIndicator = showIndicator || shouldHoldVisible

    val wantsExpanded = enabled && effectiveShowIndicator && (isHovered || isDragging || isTrackPressed)
    val effectiveIsExpanded =
        when {
            !enabled || !effectiveShowIndicator -> false
            wantsExpanded && visibility.expandDelay <= Duration.ZERO -> true
            !wantsExpanded && visibility.collapseDelay <= Duration.ZERO -> false
            else -> isExpanded
        }

    LaunchedEffect(enabled) {
        if (!enabled) {
            isHovered = false
            isDragging = false
            isTrackPressed = false
            pointerMoveChannel.tryReceive()
        }
    }

    // Reveal immediately while held visible, and linger before hiding again. Any pointer movement
    // over the content while the linger is running restarts the single lingerDuration window.
    LaunchedEffect(shouldHoldVisible, showIndicator, visibility) {
        if (shouldHoldVisible) {
            pointerMoveChannel.tryReceive()
            showIndicator = true
        } else if (showIndicator) {
            pointerMoveChannel.tryReceive()
            var lingerJob = launch {
                delay(visibility.lingerDuration)
                showIndicator = false
            }
            while (true) {
                pointerMoveChannel.receive()
                lingerJob.cancel()
                lingerJob = launch {
                    delay(visibility.lingerDuration)
                    showIndicator = false
                }
            }
        }
    }

    // Gate isExpanded on expandDelay and collapseDelay so that track padding, track background, and
    // overlay track clickability wait alongside thickness, as in Swing's ScrollBarPainter.
    LaunchedEffect(wantsExpanded, enabled, effectiveShowIndicator, visibility) {
        if (!enabled || !effectiveShowIndicator) {
            isExpanded = false
        } else if (wantsExpanded) {
            if (!isExpanded) {
                delay(visibility.expandDelay)
                isExpanded = true
            }
        } else if (isExpanded) {
            delay(visibility.collapseDelay)
            isExpanded = false
        }
    }

    val expandDelayMillis =
        if (wantsExpanded) {
            visibility.expandDelay.inWholeMilliseconds.toInt()
        } else {
            visibility.collapseDelay.inWholeMilliseconds.toInt()
        }
    val expandDurationMillis =
        visibility.expandAnimationDuration.inWholeMilliseconds.toInt().let { duration ->
            // Compose's FloatTweenSpec returns 1f immediately when durationMillis == 0, ignoring delayMillis.
            if (duration == 0 && expandDelayMillis > 0) 1 else duration
        }
    val animatedThickness by
        animateDpAsState(
            targetValue = if (wantsExpanded) visibility.trackThicknessExpanded else visibility.trackThickness,
            // The delay is what stops a pointer crossing the track from making it flicker open, as in Swing.
            animationSpec =
                tween(durationMillis = expandDurationMillis, delayMillis = expandDelayMillis, easing = LinearEasing),
            label = "scrollIndicator_thickness",
        )

    val thumbBackgroundTarget = thumbBackgroundColor(style, isOpaque, isHovered, isScrolling, effectiveShowIndicator)
    val thumbBorderTarget = thumbBorderColor(style, isOpaque, isHovered, isScrolling, effectiveShowIndicator)

    val thumbBackground by
        animateColorAsState(
            targetValue = thumbBackgroundTarget,
            animationSpec = thumbColorTween(effectiveShowIndicator, visibility),
            label = "scrollIndicator_thumbBackground",
        )
    val thumbBorder by
        animateColorAsState(
            targetValue = thumbBorderTarget,
            animationSpec = thumbColorTween(effectiveShowIndicator, visibility),
            label = "scrollIndicator_thumbBorder",
        )
    val hasVisibleBorder = !areTheSameColor(thumbBackground, thumbBorder)
    val trackBackgroundTarget = trackBackgroundColor(style, isOpaque, effectiveIsExpanded)
    val trackBackground by
        animateColorAsState(
            targetValue = trackBackgroundTarget,
            animationSpec =
                tween(visibility.trackColorAnimationDuration.inWholeMilliseconds.toInt(), easing = LinearEasing),
            label = "scrollIndicator_trackBackground",
        )

    val trackPadding =
        when {
            effectiveIsExpanded && visibility.trackPaddingExpanded != visibility.trackPadding ->
                visibility.trackPaddingExpanded
            hasVisibleBorder -> visibility.trackPaddingWithBorder
            effectiveIsExpanded -> visibility.trackPaddingExpanded
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
    val isVisible = effectiveShowIndicator || isOpaque
    val isTrackFadingOut = !effectiveIsExpanded && trackBackground != trackBackgroundTarget
    val isFadingOut = thumbBackground != thumbBackgroundTarget || thumbBorder != thumbBorderTarget || isTrackFadingOut
    val shouldDraw = isVisible || isFadingOut
    val shouldDrawTrack = (effectiveIsExpanded || isTrackFadingOut) && trackBackground.alpha > 0f
    val currentIsVisible = rememberUpdatedState(isVisible)
    val currentIsExpanded = rememberUpdatedState(effectiveIsExpanded)
    val currentIsOpaque = rememberUpdatedState(isOpaque)
    val scope = rememberCoroutineScope()
    val layoutDirection = LocalLayoutDirection.current

    val reservedThickness = scrollIndicatorReservedThickness(style, hostOs == OS.MacOS)

    // Resolved last so that every remembered value above is read unconditionally, keeping the
    // composition structure stable if the scrollable starts or stops exposing indicator metrics.
    val indicatorState = scrollState.scrollIndicatorState
    val canScrollContent: () -> Boolean = remember(indicatorState) { { indicatorState?.canScroll == true } }

    // If the content shrinks and ceases to be scrollable while hovered or pressed, clear the
    // interaction states immediately without reading canScroll during composition at rest.
    LaunchedEffect(isHovered, isDragging, isTrackPressed, indicatorState) {
        if (!isHovered && !isDragging && !isTrackPressed) return@LaunchedEffect
        snapshotFlow { indicatorState?.canScroll == true }
            .collect { canScroll ->
                if (!canScroll) {
                    isHovered = false
                    isDragging = false
                    isTrackPressed = false
                    pointerMoveChannel.tryReceive()
                }
            }
    }

    if (indicatorState == null) {
        return scrollIndicatorReservation(reservedThickness, orientation, canScrollContent)
    }

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
                isVisible = shouldDraw,
                shouldDrawTrack = shouldDrawTrack,
            )
        )
        .thenIf(enabled) {
            hoverTracking(
                state = indicatorState,
                metrics = currentMetrics,
                layoutDirection = layoutDirection,
                onPointerMove = {
                    // Prolong only: moving over hidden content must not reveal the indicator.
                    if (!currentIsOpaque.value && currentIsVisible.value) {
                        pointerMoveChannel.trySend(Unit)
                    }
                },
                onHoverChange = { isHovered = it },
            )
        }
        .thenIf(enabled) {
            thumbDrag(indicatorState, scrollState, currentMetrics, currentIsVisible, layoutDirection, scope) {
                    isDragging = it
                }
                .trackClicks(
                    state = indicatorState,
                    scrollState = scrollState,
                    metrics = currentMetrics,
                    isVisible = currentIsVisible,
                    isExpanded = currentIsExpanded,
                    isOpaque = currentIsOpaque,
                    clickBehavior = style.trackClickBehavior,
                    layoutDirection = layoutDirection,
                    scope = scope,
                    onTrackPressChange = { isTrackPressed = it },
                )
        }
        // Above the reservation, so it still sees the full node and can spot the reserved lane.
        .thenIf(enabled) {
            reservedLaneWheel(
                scrollState = scrollState,
                laneThickness = reservedThickness,
                orientation = orientation,
                layoutDirection = layoutDirection,
                reverseLayout = reverseLayout,
                canScroll = canScrollContent,
                scope = scope,
            )
        }
        // Innermost, so the draw and pointer nodes above still measure the full node and own the
        // reserved lane; only the content below is narrowed.
        .scrollIndicatorReservation(reservedThickness, orientation, canScrollContent)
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
    canScroll: () -> Boolean,
    scope: CoroutineScope,
): Modifier {
    if (laneThickness <= 0.dp) return this

    return pointerInput(laneThickness, orientation, layoutDirection, reverseLayout, scrollState) {
        val laneThicknessPx = laneThickness.roundToPx().toFloat()
        val isVertical = orientation == Orientation.Vertical
        // The reservation puts the lane on the left for a vertical indicator in RTL.
        val laneAtStart = isVertical && layoutDirection == LayoutDirection.Rtl
        awaitPointerEventScope {
            while (true) {
                val event = awaitPointerEvent()
                if (!canScroll()) continue
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
                    val delta = if (mirrored) -rawDelta else rawDelta
                    var consumed = 0f
                    scope.launch(start = CoroutineStart.UNDISPATCHED) {
                        scrollState.scroll(MutatePriority.UserInput) { consumed = scrollBy(delta) }
                    }
                    if (consumed != 0f) event.changes.forEach { it.consume() }
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

    val unconsumedChanges = changes.filter { !it.isConsumed }
    val change = unconsumedChanges.firstOrNull() ?: return null
    val crossAxisPositionPx = if (isVertical) change.position.x else change.position.y
    val crossAxisSizePx = if (isVertical) size.width else size.height
    if (!isInReservedLane(crossAxisPositionPx, crossAxisSizePx, laneThicknessPx, laneAtStart)) return null

    val wheelDelta =
        unconsumedChanges.fold(0f) { total, pointerChange ->
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
    private val shouldDrawTrack: Boolean,
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
            shouldDrawTrack,
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
            shouldDrawTrack,
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
            shouldDrawTrack == other.shouldDrawTrack
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
        result = 31 * result + shouldDrawTrack.hashCode()
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
    private var shouldDrawTrack: Boolean,
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
        shouldDrawTrack: Boolean,
    ) {
        this.state = state
        this.metrics = metrics
        this.thumbBackground = thumbBackground
        this.thumbBorder = thumbBorder
        this.trackBackground = trackBackground
        this.hasVisibleBorder = hasVisibleBorder
        this.thumbCornerRadius = thumbCornerRadius
        this.isVisible = isVisible
        this.shouldDrawTrack = shouldDrawTrack
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

        // Paint the track background while expanded or while fading out after collapse, so custom
        // non-transparent collapsed track colours do not bleed through at rest.
        if (shouldDrawTrack) {
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
    state: ScrollIndicatorState,
    metrics: State<ScrollIndicatorMetrics>,
    layoutDirection: LayoutDirection,
    onPointerMove: () -> Unit,
    onHoverChange: (Boolean) -> Unit,
): Modifier =
    pointerInput(state, layoutDirection) {
        try {
            awaitPointerEventScope {
                while (true) {
                    val event = awaitPointerEvent()
                    if (!state.canScroll) {
                        onHoverChange(false)
                        continue
                    }
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
        } finally {
            onHoverChange(false)
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
    onTrackPressChange: (Boolean) -> Unit,
): Modifier {
    val currentOrientation = metrics.value.orientation
    val currentReverseLayout = metrics.value.reverseLayout
    return pointerInput(state, scrollState, layoutDirection, clickBehavior, currentOrientation, currentReverseLayout) {
        val density = this
        val resolveTrack: () -> TrackBounds? = { trackBounds(metrics.value, layoutDirection) }
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = true, pass = PointerEventPass.Initial)
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
            onTrackPressChange(true)

            // The initial scroll runs on the composition scope, so releasing the press cannot cancel
            // it. It also latches the paging direction for the repeat loop below.
            scope.launch(start = CoroutineStart.UNDISPATCHED) {
                val pagingDirection =
                    scrollTowards(
                        state = state,
                        scrollState = scrollState,
                        resolveTrack = resolveTrack,
                        metrics = metrics,
                        density = density,
                        pressOffsetPx = pressOffsetPx,
                        clickBehavior = clickBehavior,
                    )

                if (pressed.value && state.canScroll) {
                    pressJob.value =
                        scope.launch {
                            scrollWhilePressed(
                                state = state,
                                scrollState = scrollState,
                                resolveTrack = resolveTrack,
                                metrics = metrics,
                                density = density,
                                initialPressOffsetPx = pressOffsetPx,
                                pressOffsetPx = pressOffset,
                                clickBehavior = clickBehavior,
                                pagingDirection = pagingDirection,
                            )
                        }
                    if (!pressed.value) pressJob.value?.cancel()
                }
            }

            try {
                while (state.canScroll) {
                    val event = awaitPointerEvent(PointerEventPass.Initial)
                    if (!state.canScroll) break
                    val change = event.changes.firstOrNull { it.id == down.id } ?: break
                    change.consume()
                    if (!change.pressed) break
                    val liveTrack = resolveTrack() ?: track
                    pressOffset.floatValue = liveTrack.axis.mainAxisOffset(change.position)
                }
            } finally {
                // Stop following the press once it ends; any scroll it already started still runs.
                pressed.value = false
                pressJob.value?.cancel()
                onTrackPressChange(false)
            }
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
    resolveTrack: () -> TrackBounds?,
    metrics: State<ScrollIndicatorMetrics>,
    density: Density,
    initialPressOffsetPx: Float,
    pressOffsetPx: MutableFloatState,
    clickBehavior: TrackClickBehavior,
    pagingDirection: Int,
) {
    when (clickBehavior) {
        // Track the last applied offset rather than dropping the first emission, so a pointer move
        // that arrived while the initial scrollTowards was suspending is not lost.
        TrackClickBehavior.JumpToSpot -> {
            var lastAppliedOffsetPx = initialPressOffsetPx
            snapshotFlow { pressOffsetPx.floatValue }
                .collectLatest { offsetPx ->
                    if (!state.canScroll) return@collectLatest
                    if (offsetPx != lastAppliedOffsetPx) {
                        lastAppliedOffsetPx = offsetPx
                        scrollTowards(state, scrollState, resolveTrack, metrics, density, offsetPx, clickBehavior)
                    }
                }
        }

        TrackClickBehavior.NextPage -> {
            if (pagingDirection == 0) return
            delay(DELAY_BEFORE_SECOND_SCROLL_ON_TRACK_PRESS)
            while (state.canScroll) {
                val scrolled =
                    scrollTowards(
                        state = state,
                        scrollState = scrollState,
                        resolveTrack = resolveTrack,
                        metrics = metrics,
                        density = density,
                        pressOffsetPx = pressOffsetPx.floatValue,
                        clickBehavior = clickBehavior,
                        pagingDirection = pagingDirection,
                    )
                if (scrolled == 0) {
                    if (!state.canScroll) break
                    // The thumb has reached the press, or the content is at its limit. Idle instead of
                    // ticking, but stay ready: the Swing scrollbar restarts its repeat timer on every
                    // drag (`DefaultScrollBarUI.mouseDragged` -> `startScrollTimerIfNecessary`), so
                    // dragging further along the track or growing the content has to resume paging.
                    val restingState = Triple(pressOffsetPx.floatValue, state.contentSize, state.viewportSize)
                    snapshotFlow { Triple(pressOffsetPx.floatValue, state.contentSize, state.viewportSize) }
                        .first { it != restingState }
                    if (!state.canScroll) break
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
    resolveTrack: () -> TrackBounds?,
    metrics: State<ScrollIndicatorMetrics>,
    density: Density,
    pressOffsetPx: Float,
    clickBehavior: TrackClickBehavior,
    pagingDirection: Int = 0,
): Int {
    if (!state.canScroll) return 0
    val track = resolveTrack() ?: return 0
    val currentMetrics = metrics.value
    val minThumbLengthPx = with(density) { currentMetrics.minThumbLength.toPx().roundToInt() }
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
            val delta = (target.roundToInt() - state.scrollOffset).toFloat()
            if (delta != 0f) {
                scrollState.scrollByUserInput(if (currentMetrics.reverseLayout) -delta else delta)
            }
            return 0
        }

        TrackClickBehavior.NextPage -> {
            val direction = pageDirectionTowardPointer(pressOffsetPx, thumb.offsetPx, thumb.sizePx)
            if (direction == 0) return 0
            if (pagesBackPastPress(pagingDirection, direction)) return 0

            val pageDelta = direction * state.viewportSize.toFloat()
            val consumed = scrollState.scrollByUserInput(if (currentMetrics.reverseLayout) -pageDelta else pageDelta)
            // At the scroll limit nothing moves, so report that no paging happened.
            return if (consumed != 0f) direction else 0
        }
    }
}

private suspend fun ScrollableState.scrollByUserInput(pixels: Float): Float {
    var consumed = 0f
    scroll(MutatePriority.UserInput) { consumed = scrollBy(pixels) }
    return consumed
}

private fun Modifier.thumbDrag(
    state: ScrollIndicatorState,
    scrollState: ScrollableState,
    metrics: State<ScrollIndicatorMetrics>,
    isVisible: State<Boolean>,
    layoutDirection: LayoutDirection,
    scope: CoroutineScope,
    onDraggingChange: (Boolean) -> Unit,
): Modifier {
    val currentOrientation = metrics.value.orientation
    val currentReverseLayout = metrics.value.reverseLayout
    return pointerInput(state, scrollState, layoutDirection, currentOrientation, currentReverseLayout) {
        val density = this
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = true, pass = PointerEventPass.Initial)
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
            down.consume()

            // Where inside the thumb the drag started. The Swing scrollbar keeps that point under the
            // pointer for the whole drag (`DefaultScrollBarUI.setValueFrom` clamps `y - myOffset`), so
            // grabbing an edge must not snap the thumb's centre to the cursor.
            val grabOffsetPx = track.axis.mainAxisOffset(down.position) - thumb.offsetPx

            val activeScrollScope = mutableStateOf<ScrollScope?>(null)
            var pendingPointerOffsetPx: Float? = null
            val scrollSessionJob =
                scope.launch(start = CoroutineStart.UNDISPATCHED) {
                    scrollState.scroll(MutatePriority.UserInput) {
                        activeScrollScope.value = this
                        pendingPointerOffsetPx?.let { pendingOffsetPx ->
                            pendingPointerOffsetPx = null
                            val liveMetrics = metrics.value
                            val liveTrack = trackBounds(liveMetrics, layoutDirection)
                            if (liveTrack != null && state.canScroll) {
                                val liveMinThumbLengthPx =
                                    with(density) { liveMetrics.minThumbLength.toPx().roundToInt() }
                                moveThumbToPointer(
                                    pointerOffsetPx = pendingOffsetPx,
                                    grabOffsetPx = grabOffsetPx,
                                    track = liveTrack,
                                    state = state,
                                    scrollScope = this,
                                    metrics = liveMetrics,
                                    minThumbLengthPx = liveMinThumbLengthPx,
                                )
                            }
                        }
                        try {
                            snapshotFlow { state.canScroll }.first { !it }
                        } finally {
                            activeScrollScope.value = null
                        }
                    }
                }

            if (!scrollSessionJob.isActive) return@awaitEachGesture

            onDraggingChange(true)
            try {
                var dragging = true
                while (dragging && state.canScroll && scrollSessionJob.isActive) {
                    val event = awaitPointerEvent(PointerEventPass.Initial)
                    val change =
                        event.changes
                            .firstOrNull { it.id == down.id }
                            ?.takeIf { state.canScroll && scrollSessionJob.isActive }
                    if (change == null) {
                        dragging = false
                    } else {
                        change.consume()
                        dragging = change.pressed
                        val liveMetrics = metrics.value
                        val liveTrack =
                            if (dragging && change.positionChangeIgnoreConsumed() != Offset.Zero) {
                                trackBounds(liveMetrics, layoutDirection)
                            } else {
                                null
                            }
                        if (liveTrack != null) {
                            val liveMinThumbLengthPx = liveMetrics.minThumbLength.toPx().roundToInt()
                            val pointerOffsetPx = liveTrack.axis.mainAxisOffset(change.position)
                            val scrollScope = activeScrollScope.value
                            if (scrollScope != null) {
                                // Absolute positioning: each event maps the pointer onto the scroll range, so
                                // overshooting an end and coming back cannot drift.
                                moveThumbToPointer(
                                    pointerOffsetPx = pointerOffsetPx,
                                    grabOffsetPx = grabOffsetPx,
                                    track = liveTrack,
                                    state = state,
                                    scrollScope = scrollScope,
                                    metrics = liveMetrics,
                                    minThumbLengthPx = liveMinThumbLengthPx,
                                )
                            } else {
                                pendingPointerOffsetPx = pointerOffsetPx
                            }
                        }
                    }
                }
            } finally {
                scrollSessionJob.cancel()
                onDraggingChange(false)
            }
        }
    }
}

/** Scrolls so that the point [grabOffsetPx] into the thumb lands on [pointerOffsetPx]. */
private fun moveThumbToPointer(
    pointerOffsetPx: Float,
    grabOffsetPx: Float,
    track: TrackBounds,
    state: ScrollIndicatorState,
    scrollScope: ScrollScope,
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
    // Round target to an integer before subtracting the integer scrollOffset, so fractional sub-pixel
    // remainders do not accumulate in ScrollState.scrollBy across stationary or near-stationary events.
    val visualDeltaPx = (target.roundToInt() - state.scrollOffset).toFloat()
    val deltaPx = if (metrics.reverseLayout) -visualDeltaPx else visualDeltaPx
    if (deltaPx != 0f) {
        scrollScope.scrollBy(deltaPx)
    }
}

private fun trackBackgroundColor(style: ScrollbarStyle, isOpaque: Boolean, isExpanded: Boolean): Color =
    if (isOpaque) {
        if (isExpanded) style.colors.trackOpaqueBackgroundHovered else style.colors.trackOpaqueBackground
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
        if (showIndicator) style.colors.thumbBackgroundActive else style.colors.thumbBackground.copy(alpha = 0f)
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
        if (showIndicator) style.colors.thumbBorderActive else style.colors.thumbBorder.copy(alpha = 0f)
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
