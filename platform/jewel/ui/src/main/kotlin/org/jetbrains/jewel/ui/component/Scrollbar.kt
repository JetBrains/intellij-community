package org.jetbrains.jewel.ui.component

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.ScrollableState
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitHorizontalDragOrCancellation
import androidx.compose.foundation.gestures.awaitVerticalDragOrCancellation
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.gestures.scrollable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.DragInteraction
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.foundation.text.TextFieldScrollState
import androidx.compose.foundation.v2.ScrollbarAdapter
import androidx.compose.foundation.v2.maxScrollOffset
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.MeasurePolicy
import androidx.compose.ui.layout.layoutId
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.hideFromAccessibility
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.constrainHeight
import androidx.compose.ui.unit.constrainWidth
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.jetbrains.jewel.foundation.modifier.thenIf
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.component.styling.ScrollbarStyle
import org.jetbrains.jewel.ui.component.styling.ScrollbarVisibility
import org.jetbrains.jewel.ui.component.styling.ScrollbarVisibility.AlwaysVisible
import org.jetbrains.jewel.ui.component.styling.TrackClickBehavior
import org.jetbrains.jewel.ui.component.styling.TrackClickBehavior.JumpToSpot
import org.jetbrains.jewel.ui.component.styling.TrackClickBehavior.NextPage
import org.jetbrains.jewel.ui.theme.scrollbarStyle

/**
 * A vertical scrollbar that can be tied to a [ScrollableState].
 *
 * @param scrollState The [ScrollableState] to control
 * @param modifier The modifier to apply to this layout node
 * @param reverseLayout `true` to reverse the direction of the scrollbar, `false` otherwise.
 * @param enabled `true` to enable the scrollbar, `false` otherwise.
 * @param interactionSource The [MutableInteractionSource] that will be used to dispatch events.
 * @param style The [ScrollbarStyle] to use for this scrollbar.
 * @param keepVisible `true` to keep the scrollbar visible even when not scrolling, `false` otherwise.
 */
@Composable
public fun VerticalScrollbar(
    scrollState: ScrollableState,
    modifier: Modifier = Modifier,
    reverseLayout: Boolean = false,
    enabled: Boolean = true,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
    style: ScrollbarStyle = JewelTheme.scrollbarStyle,
    keepVisible: Boolean = false,
) {
    BaseScrollbar(
        scrollState = scrollState,
        reverseLayout = reverseLayout,
        enabled = enabled,
        interactionSource = interactionSource,
        isVertical = true,
        style = style,
        keepVisible = keepVisible,
        modifier = modifier,
    )
}

/**
 * A horizontal scrollbar that can be tied to a [ScrollableState].
 *
 * @param scrollState The [ScrollableState] to control.
 * @param modifier The modifier to apply to this layout node.
 * @param reverseLayout `true` to reverse the direction of the scrollbar, `false` otherwise.
 * @param enabled `true` to enable the scrollbar, `false` otherwise.
 * @param interactionSource The [MutableInteractionSource] that will be used to dispatch events.
 * @param style The [ScrollbarStyle] to use for this scrollbar.
 * @param keepVisible `true` to keep the scrollbar visible even when not scrolling, `false` otherwise.
 */
@Composable
public fun HorizontalScrollbar(
    scrollState: ScrollableState,
    modifier: Modifier = Modifier,
    reverseLayout: Boolean = false,
    enabled: Boolean = true,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
    style: ScrollbarStyle = JewelTheme.scrollbarStyle,
    keepVisible: Boolean = false,
) {
    BaseScrollbar(
        scrollState = scrollState,
        reverseLayout = reverseLayout,
        enabled = enabled,
        interactionSource = interactionSource,
        isVertical = false,
        style = style,
        keepVisible = keepVisible,
        modifier = modifier,
    )
}

@Composable
private fun BaseScrollbar(
    scrollState: ScrollableState,
    reverseLayout: Boolean,
    enabled: Boolean,
    interactionSource: MutableInteractionSource,
    isVertical: Boolean,
    style: ScrollbarStyle,
    keepVisible: Boolean,
    modifier: Modifier = Modifier,
) {
    val dragInteraction = remember { mutableStateOf<DragInteraction.Start?>(null) }
    DisposableEffect(interactionSource) {
        onDispose {
            dragInteraction.value?.let { interaction ->
                interactionSource.tryEmit(DragInteraction.Cancel(interaction))
                dragInteraction.value = null
            }
        }
    }

    val visibilityStyle = style.scrollbarVisibility
    val isOpaque = visibilityStyle is AlwaysVisible
    var isExpanded by remember { mutableStateOf(false) }
    val isHovered by interactionSource.collectIsHoveredAsState()
    var showScrollbar by remember { mutableStateOf(false) }

    val isDragging = dragInteraction.value != null
    val isScrolling = scrollState.isScrollInProgress || isDragging
    val isActive = isOpaque || isScrolling || (keepVisible && showScrollbar)

    if (isHovered && showScrollbar) isExpanded = true

    LaunchedEffect(isActive, isHovered, showScrollbar) {
        val isVisibleAndHovered = showScrollbar && isHovered
        if (isActive || isVisibleAndHovered) {
            showScrollbar = true
        } else {
            launch {
                delay(visibilityStyle.lingerDuration)
                showScrollbar = false
                isExpanded = false
            }
        }
    }

    val animatedThickness by
        animateDpAsState(
            if (isExpanded) visibilityStyle.trackThicknessExpanded else visibilityStyle.trackThickness,
            tween(visibilityStyle.expandAnimationDuration.inWholeMilliseconds.toInt(), easing = LinearEasing),
            "scrollbar_thickness",
        )

    val adapter =
        when (scrollState) {
            is LazyListState -> rememberScrollbarAdapter(scrollState)
            is LazyGridState -> rememberScrollbarAdapter(scrollState)
            is ScrollState -> rememberScrollbarAdapter(scrollState)
            is TextFieldScrollState -> rememberScrollbarAdapter(scrollState)
            else -> error("Unsupported scroll state type: ${scrollState::class.qualifiedName}")
        }

    with(LocalDensity.current) {
        var containerSize by remember { mutableIntStateOf(0) }
        val thumbMinHeight = style.metrics.minThumbLength.toPx()

        val coroutineScope = rememberCoroutineScope()
        val sliderAdapter =
            remember(adapter, containerSize, thumbMinHeight, reverseLayout, isVertical, coroutineScope) {
                SliderAdapter(adapter, containerSize, thumbMinHeight, reverseLayout, isVertical, coroutineScope)
            }

        val thumbBackgroundColor = getThumbBackgroundColor(isOpaque, isHovered, isScrolling, style, showScrollbar)
        val thumbBorderColor = getThumbBorderColor(isOpaque, isHovered, isScrolling, style, showScrollbar)
        val hasVisibleBorder = !areTheSameColor(thumbBackgroundColor, thumbBorderColor)
        val trackPadding =
            if (hasVisibleBorder) visibilityStyle.trackPaddingWithBorder else visibilityStyle.trackPadding

        val thumbThicknessPx =
            if (isVertical) {
                    val layoutDirection = LocalLayoutDirection.current
                    animatedThickness -
                        trackPadding.calculateLeftPadding(layoutDirection) -
                        trackPadding.calculateRightPadding(layoutDirection)
                } else {
                    animatedThickness - trackPadding.calculateTopPadding() - trackPadding.calculateBottomPadding()
                }
                .roundToPx()

        val measurePolicy =
            if (isVertical) {
                remember(sliderAdapter, thumbThicknessPx) {
                    verticalMeasurePolicy(sliderAdapter, { containerSize = it }, thumbThicknessPx)
                }
            } else {
                remember(sliderAdapter, thumbThicknessPx) {
                    horizontalMeasurePolicy(sliderAdapter, { containerSize = it }, thumbThicknessPx)
                }
            }

        val canScroll = sliderAdapter.thumbSize < containerSize

        val trackBackground by
            animateColorAsState(
                targetValue = getTrackColor(isOpaque, isDragging, isHovered, style, isExpanded),
                animationSpec = trackColorTween(visibilityStyle),
                label = "scrollbar_trackBackground",
            )

        Layout(
            content = {
                Thumb(
                    showScrollbar,
                    visibilityStyle,
                    canScroll,
                    enabled,
                    interactionSource,
                    dragInteraction,
                    sliderAdapter,
                    thumbBackgroundColor,
                    thumbBorderColor,
                    hasVisibleBorder,
                    style.metrics.thumbCornerSize,
                )
            },
            modifier =
                modifier
                    .semantics { hideFromAccessibility() }
                    .thenIf(showScrollbar && canScroll && isExpanded) { background(trackBackground) }
                    .scrollable(
                        state = scrollState,
                        orientation = if (isVertical) Orientation.Vertical else Orientation.Horizontal,
                        enabled = enabled,
                        reverseDirection = true, // Not sure why it's needed, but it is — TODO revisit this
                    )
                    .padding(trackPadding)
                    .hoverable(interactionSource = interactionSource)
                    .thenIf(enabled && showScrollbar) {
                        scrollOnPressTrack(style.trackClickBehavior, isVertical, reverseLayout, sliderAdapter)
                    },
            measurePolicy = measurePolicy,
        )
    }
}

private fun getTrackColor(
    isOpaque: Boolean,
    isDragging: Boolean,
    isHovered: Boolean,
    style: ScrollbarStyle,
    isExpanded: Boolean,
) =
    if (isOpaque) {
        if (isHovered || isDragging) {
            style.colors.trackOpaqueBackgroundHovered
        } else {
            style.colors.trackOpaqueBackground
        }
    } else {
        if (isExpanded) {
            style.colors.trackBackgroundExpanded
        } else {
            style.colors.trackBackground
        }
    }

private fun getThumbBackgroundColor(
    isOpaque: Boolean,
    isHovered: Boolean,
    isScrolling: Boolean,
    style: ScrollbarStyle,
    showScrollbar: Boolean,
) =
    if (isOpaque) {
        if (isHovered || isScrolling) {
            style.colors.thumbOpaqueBackgroundHovered
        } else {
            style.colors.thumbOpaqueBackground
        }
    } else {
        if (showScrollbar) {
            style.colors.thumbBackgroundActive
        } else {
            style.colors.thumbBackground
        }
    }

private fun getThumbBorderColor(
    isOpaque: Boolean,
    isHovered: Boolean,
    isScrolling: Boolean,
    style: ScrollbarStyle,
    showScrollbar: Boolean,
) =
    if (isOpaque) {
        if (isHovered || isScrolling) {
            style.colors.thumbOpaqueBorderHovered
        } else {
            style.colors.thumbOpaqueBorder
        }
    } else {
        if (showScrollbar) {
            style.colors.thumbBorderActive
        } else {
            style.colors.thumbBorder
        }
    }

private fun areTheSameColor(first: Color, second: Color) = first.toArgb() == second.toArgb()

@Suppress("MutableStateParam") // To fix in JEWEL-923
@Composable
private fun Thumb(
    showScrollbar: Boolean,
    visibilityStyle: ScrollbarVisibility,
    canScroll: Boolean,
    enabled: Boolean,
    interactionSource: MutableInteractionSource,
    dragInteraction: MutableState<DragInteraction.Start?>,
    sliderAdapter: SliderAdapter,
    thumbBackgroundColor: Color,
    thumbBorderColor: Color,
    hasVisibleBorder: Boolean,
    cornerSize: CornerSize,
) {
    val background by
        animateColorAsState(
            targetValue = thumbBackgroundColor,
            animationSpec = thumbColorTween(showScrollbar, visibilityStyle),
            label = "scrollbar_thumbBackground",
        )

    val border by
        animateColorAsState(
            targetValue = thumbBorderColor,
            animationSpec = thumbColorTween(showScrollbar, visibilityStyle),
            label = "scrollbar_thumbBorder",
        )

    val borderWidth = 1.dp
    val density = LocalDensity.current
    Box(
        Modifier.layoutId("thumb")
            .thenIf(canScroll) { drawThumb(background, borderWidth, border, hasVisibleBorder, cornerSize, density) }
            .thenIf(enabled) { scrollbarDrag(interactionSource, dragInteraction, sliderAdapter) }
    )
}

private fun Modifier.drawThumb(
    backgroundColor: Color,
    borderWidth: Dp,
    borderColor: Color,
    hasVisibleBorder: Boolean,
    cornerSize: CornerSize,
    density: Density,
) = drawBehind {
    val borderWidthPx = if (hasVisibleBorder) borderWidth.toPx() else 0f

    // First, draw the background, leaving room for the border around it
    val bgCornerRadius = CornerRadius((cornerSize.toPx(size, density) - borderWidthPx * 2).coerceAtLeast(0f))
    drawRoundRect(
        color = backgroundColor,
        topLeft = Offset(borderWidthPx, borderWidthPx),
        size = Size(size.width - borderWidthPx * 2, size.height - borderWidthPx * 2f),
        cornerRadius = bgCornerRadius,
    )

    // Then, draw the border itself
    if (hasVisibleBorder) {
        val strokeCornerRadius = CornerRadius(cornerSize.toPx(size, density))
        drawRoundRect(
            color = borderColor,
            topLeft = Offset(borderWidthPx / 2, borderWidthPx / 2),
            size = Size(size.width - borderWidthPx, size.height - borderWidthPx),
            cornerRadius = strokeCornerRadius,
            style = Stroke(borderWidthPx),
        )
    }
}

private fun trackColorTween(visibility: ScrollbarVisibility) =
    tween<Color>(visibility.trackColorAnimationDuration.inWholeMilliseconds.toInt(), easing = LinearEasing)

private fun thumbColorTween(showScrollbar: Boolean, visibility: ScrollbarVisibility) =
    tween<Color>(
        durationMillis =
            if (visibility is AlwaysVisible || !showScrollbar) {
                visibility.thumbColorAnimationDuration.inWholeMilliseconds.toInt()
            } else 0,
        delayMillis =
            when {
                visibility is AlwaysVisible && !showScrollbar -> visibility.lingerDuration.inWholeMilliseconds.toInt()
                else -> 0
            },
        easing = LinearEasing,
    )

// ===========================================================================
// Note: most of the code below is copied and adapted from the stock scrollbar
// ===========================================================================

private val SliderAdapter.thumbPixelRange: IntRange
    get() {
        val start = position.roundToInt()
        val endExclusive = start + thumbSize.roundToInt()

        return (start until endExclusive)
    }

private val IntRange.size
    get() = last + 1 - first

private fun verticalMeasurePolicy(sliderAdapter: SliderAdapter, setContainerSize: (Int) -> Unit, thumbThickness: Int) =
    MeasurePolicy { measurables, constraints ->
        setContainerSize(constraints.maxHeight)
        val pixelRange = sliderAdapter.thumbPixelRange
        val placeable =
            measurables.first().measure(Constraints.fixed(constraints.constrainWidth(thumbThickness), pixelRange.size))
        layout(placeable.width, constraints.maxHeight) { placeable.place(0, pixelRange.first) }
    }

private fun horizontalMeasurePolicy(
    sliderAdapter: SliderAdapter,
    setContainerSize: (Int) -> Unit,
    thumbThickness: Int,
) = MeasurePolicy { measurables, constraints ->
    setContainerSize(constraints.maxWidth)
    val pixelRange = sliderAdapter.thumbPixelRange
    val placeable =
        measurables.first().measure(Constraints.fixed(pixelRange.size, constraints.constrainHeight(thumbThickness)))
    layout(constraints.maxWidth, placeable.height) { placeable.place(pixelRange.first, 0) }
}

@Suppress("ModifierComposed") // To fix in JEWEL-921
private fun Modifier.scrollbarDrag(
    interactionSource: MutableInteractionSource,
    draggedInteraction: MutableState<DragInteraction.Start?>,
    sliderAdapter: SliderAdapter,
): Modifier = composed {
    val currentInteractionSource by rememberUpdatedState(interactionSource)
    val currentDraggedInteraction by rememberUpdatedState(draggedInteraction)
    val currentSliderAdapter by rememberUpdatedState(sliderAdapter)

    pointerInput(Unit) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false)
            val interaction = DragInteraction.Start()
            currentInteractionSource.tryEmit(interaction)
            currentDraggedInteraction.value = interaction
            currentSliderAdapter.onDragStarted()
            val isSuccess =
                drag(down.id) { change ->
                    currentSliderAdapter.onDragDelta(change.positionChange())
                    change.consume()
                }
            val finishInteraction =
                if (isSuccess) {
                    DragInteraction.Stop(interaction)
                } else {
                    DragInteraction.Cancel(interaction)
                }
            currentInteractionSource.tryEmit(finishInteraction)
            currentDraggedInteraction.value = null
        }
    }
}

@Suppress("ModifierComposed") // To fix in JEWEL-921
private fun Modifier.scrollOnPressTrack(
    clickBehavior: TrackClickBehavior,
    isVertical: Boolean,
    reverseLayout: Boolean,
    sliderAdapter: SliderAdapter,
) = composed {
    val coroutineScope = rememberCoroutineScope()
    val scroller =
        remember(sliderAdapter, coroutineScope, reverseLayout, clickBehavior) {
            TrackPressScroller(coroutineScope, sliderAdapter, reverseLayout, clickBehavior)
        }

    Modifier.pointerInput(scroller) { detectScrollViaTrackGestures(isVertical = isVertical, scroller = scroller) }
}

/** Responsible for scrolling when the scrollbar track is pressed (outside the thumb). */
private class TrackPressScroller(
    private val coroutineScope: CoroutineScope,
    private val sliderAdapter: SliderAdapter,
    private val reverseLayout: Boolean,
    private val clickBehavior: TrackClickBehavior,
) {
    /** The current direction of scroll (1: down/right, -1: up/left, 0: not scrolling) */
    private var direction = 0

    /** The currently pressed location (in pixels) on the scrollable axis. */
    private var offset: Float? = null

    /** The job that keeps scrolling while the track is pressed. */
    private var job: Job? = null

    /** Calculates the direction of scrolling towards the given offset (in pixels). */
    private fun directionOfScrollTowards(offset: Float): Int {
        val pixelRange = sliderAdapter.thumbPixelRange
        return when {
            offset < pixelRange.first -> if (reverseLayout) 1 else -1
            offset > pixelRange.last -> if (reverseLayout) -1 else 1
            else -> 0
        }
    }

    /** Scrolls once towards the current offset, if it matches the direction of the current gesture. */
    private suspend fun scrollTowardsCurrentOffset() {
        offset?.let {
            val currentDirection = directionOfScrollTowards(it)
            if (currentDirection != direction) {
                return
            }
            with(sliderAdapter.adapter) { scrollTo(scrollOffset + currentDirection * viewportSize) }
        }
    }

    /** Starts the job that scrolls continuously towards the current offset. */
    private fun startScrollingByPage() {
        job?.cancel()
        job =
            coroutineScope.launch {
                scrollTowardsCurrentOffset()
                delay(DELAY_BEFORE_SECOND_SCROLL_ON_TRACK_PRESS)
                while (true) {
                    scrollTowardsCurrentOffset()
                    delay(DELAY_BETWEEN_SCROLLS_ON_TRACK_PRESS)
                }
            }
    }

    /** Invoked on the first press for a gesture. */
    fun onPress(offset: Float) {
        this.offset = offset
        this.direction = directionOfScrollTowards(offset)

        if (direction == 0) return

        if (clickBehavior == NextPage) startScrollingByPage()
        else if (clickBehavior == JumpToSpot) scrollToOffset(offset)
    }

    /** Invoked when the pointer moves while pressed during the gesture. */
    fun onMovePressed(offset: Float) {
        this.offset = offset
        if (clickBehavior == JumpToSpot) scrollToOffset(offset)
    }

    /** Cleans up when the gesture finishes. */
    private fun cleanupAfterGesture() {
        job?.cancel()
        direction = 0
        offset = null
    }

    /** Invoked when the button is released. */
    fun onRelease() {
        cleanupAfterGesture()
    }

    private fun scrollToOffset(offset: Float) {
        job?.cancel()
        job =
            coroutineScope.launch {
                val contentSize = sliderAdapter.adapter.contentSize
                val scrollOffset = offset / sliderAdapter.adapter.viewportSize * contentSize
                sliderAdapter.adapter.scrollTo(scrollOffset)
            }
    }

    /** Invoked when the gesture is cancelled. */
    fun onGestureCancelled() {
        cleanupAfterGesture()
        // Maybe revert to the initial position?
    }
}

/**
 * Detects the pointer events relevant for the "scroll by pressing on the track outside the thumb" gesture and calls the
 * corresponding methods in the [scroller].
 */
private suspend fun PointerInputScope.detectScrollViaTrackGestures(isVertical: Boolean, scroller: TrackPressScroller) {
    fun Offset.onScrollAxis() = if (isVertical) y else x

    awaitEachGesture {
        val down = awaitFirstDown()
        scroller.onPress(down.position.onScrollAxis())

        while (true) {
            val drag =
                if (isVertical) {
                    awaitVerticalDragOrCancellation(down.id)
                } else {
                    awaitHorizontalDragOrCancellation(down.id)
                }

            if (drag == null) {
                scroller.onGestureCancelled()
                break
            } else if (!drag.pressed) {
                scroller.onRelease()
                break
            } else {
                scroller.onMovePressed(drag.position.onScrollAxis())
            }
        }
    }
}

/** The delay between the 1st and 2nd scroll while the scrollbar track is pressed outside the thumb. */
internal const val DELAY_BEFORE_SECOND_SCROLL_ON_TRACK_PRESS: Long = 300L

/** The delay between each subsequent (after the 2nd) scroll while the scrollbar track is pressed outside the thumb. */
internal const val DELAY_BETWEEN_SCROLLS_ON_TRACK_PRESS: Long = 100L

internal class SliderAdapter(
    val adapter: ScrollbarAdapter,
    private val trackSize: Int,
    private val minHeight: Float,
    private val reverseLayout: Boolean,
    private val isVertical: Boolean,
    private val coroutineScope: CoroutineScope,
) {
    private val contentSize
        get() = adapter.contentSize

    private val visiblePart: Double
        get() {
            val contentSize = contentSize
            return if (contentSize == 0.0) {
                1.0
            } else {
                (adapter.viewportSize / contentSize).coerceAtMost(1.0)
            }
        }

    val thumbSize
        get() = (trackSize * visiblePart).coerceAtLeast(minHeight.toDouble())

    private val scrollScale: Double
        get() {
            val extraScrollbarSpace = trackSize - thumbSize
            val extraContentSpace = adapter.maxScrollOffset // == contentSize - viewportSize
            return if (extraContentSpace == 0.0) 1.0 else extraScrollbarSpace / extraContentSpace
        }

    private val rawPosition: Double
        get() = scrollScale * adapter.scrollOffset

    val position: Double
        get() = if (reverseLayout) trackSize - thumbSize - rawPosition else rawPosition

    val bounds
        get() = position..position + thumbSize

    // How much of the current drag was ignored because we've reached the end of the scrollbar area
    private var unscrolledDragDistance = 0.0

    /** Called when the thumb dragging starts */
    fun onDragStarted() {
        unscrolledDragDistance = 0.0
    }

    private suspend fun setPosition(value: Double) {
        val rawPosition =
            if (reverseLayout) {
                trackSize - thumbSize - value
            } else {
                value
            }
        adapter.scrollTo(rawPosition / scrollScale)
    }

    private val dragMutex = Mutex()

    /** Called on every movement while dragging the thumb */
    fun onDragDelta(offset: Offset) {
        coroutineScope.launch(start = CoroutineStart.UNDISPATCHED) {
            // Mutex is used to ensure that all earlier drag deltas were applied
            // before calculating a new raw position
            dragMutex.withLock {
                val dragDelta = if (isVertical) offset.y else offset.x
                val maxScrollPosition = adapter.maxScrollOffset * scrollScale
                val currentPosition = position
                val targetPosition =
                    (currentPosition + dragDelta + unscrolledDragDistance).coerceIn(0.0, maxScrollPosition)
                val sliderDelta = targetPosition - currentPosition

                // Have to add to position for smooth content scroll if the items are of different
                // size
                val newPos = position + sliderDelta
                setPosition(newPos)
                unscrolledDragDistance += dragDelta - sliderDelta
            }
        }
    }
}
