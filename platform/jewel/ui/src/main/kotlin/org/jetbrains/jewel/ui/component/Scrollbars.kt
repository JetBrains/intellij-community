package org.jetbrains.jewel.ui.component

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.ScrollableState
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitHorizontalDragOrCancellation
import androidx.compose.foundation.gestures.awaitVerticalDragOrCancellation
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.gestures.scrollable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.DragInteraction
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.TextFieldScrollState
import androidx.compose.foundation.v2.ScrollbarAdapter
import androidx.compose.foundation.v2.maxScrollOffset
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.MeasurePolicy
import androidx.compose.ui.layout.layoutId
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.constrainHeight
import androidx.compose.ui.unit.constrainWidth
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.component.styling.ScrollbarStyle
import org.jetbrains.jewel.ui.component.styling.ScrollbarVisibility.AlwaysVisible
import org.jetbrains.jewel.ui.component.styling.ScrollbarVisibility.WhenScrolling
import org.jetbrains.jewel.ui.component.styling.TrackClickBehavior.JumpToSpot
import org.jetbrains.jewel.ui.component.styling.TrackClickBehavior.NextPage
import org.jetbrains.jewel.ui.theme.scrollbarStyle
import org.jetbrains.jewel.ui.util.thenIf
import kotlin.math.roundToInt

@Composable
public fun VerticalScrollbar(
    scrollState: ScrollableState,
    modifier: Modifier = Modifier,
    reverseLayout: Boolean = false,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
    style: ScrollbarStyle = JewelTheme.scrollbarStyle,
) {
    MyScrollbar(
        scrollState = scrollState,
        modifier = modifier,
        reverseLayout = reverseLayout,
        interactionSource = interactionSource,
        isVertical = true,
        style = style,
    )
}

@Composable
public fun HorizontalScrollbar(
    scrollState: ScrollableState,
    modifier: Modifier = Modifier,
    reverseLayout: Boolean = false,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
    style: ScrollbarStyle = JewelTheme.scrollbarStyle,
) {
    MyScrollbar(
        scrollState = scrollState,
        modifier = modifier,
        reverseLayout = reverseLayout,
        interactionSource = interactionSource,
        isVertical = false,
        style = style,
    )
}

@Composable
private fun MyScrollbar(
    scrollState: ScrollableState,
    modifier: Modifier = Modifier,
    reverseLayout: Boolean = false,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
    isVertical: Boolean,
    style: ScrollbarStyle,
) {
    // Click to scroll
    var clickPosition by remember { mutableIntStateOf(0) }
    val scrollbarWidth = remember { mutableIntStateOf(0) }
    val scrollbarHeight = remember { mutableIntStateOf(0) }
    LaunchedEffect(clickPosition) {
        if (scrollState is ScrollState) {
            if (scrollbarHeight.value == 0) return@LaunchedEffect

            val jumpTo = when (style.trackClickBehavior) {
                NextPage -> scrollbarHeight.value + scrollState.viewportSize
                JumpToSpot -> (scrollState.maxValue * clickPosition) / scrollbarHeight.value
            }

            scrollState.scrollTo(jumpTo)
        }
    }

    // Visibility, hover and fade out
    var visible by remember { mutableStateOf(scrollState.canScrollBackward) }
    val hovered = interactionSource.collectIsHoveredAsState().value
    var trackIsVisible by remember { mutableStateOf(false) }

    val animatedAlpha by animateFloatAsState(
        targetValue = if (visible) 1.0f else 0f,
        label = "alpha",
    )

    LaunchedEffect(scrollState.isScrollInProgress, hovered, style.scrollbarVisibility) {
        when (style.scrollbarVisibility) {
            AlwaysVisible -> {
                visible = true
                trackIsVisible = true
            }

            is WhenScrolling -> {
                when {
                    scrollState.isScrollInProgress -> visible = true
                    hovered -> {
                        visible = true
                        trackIsVisible = true
                    }

                    !hovered -> {
                        delay(style.scrollbarVisibility.lingerDuration)
                        trackIsVisible = false
                        visible = false
                    }

                    !scrollState.isScrollInProgress && !hovered -> {
                        delay(style.scrollbarVisibility.lingerDuration)
                        visible = false
                    }
                }
            }
        }

        when {
            scrollState.isScrollInProgress -> visible = true
            hovered -> {
                visible = true
                trackIsVisible = true
            }
        }
    }

    val adapter =
        when (scrollState) {
            is LazyListState -> rememberScrollbarAdapter(scrollState)
            is LazyGridState -> rememberScrollbarAdapter(scrollState)
            is ScrollState -> rememberScrollbarAdapter(scrollState)
            is TextFieldScrollState -> rememberScrollbarAdapter(scrollState)
            else -> error("Unsupported scroll state type: ${scrollState::class}")
        }

    val thumbWidth = if (trackIsVisible) style.metrics.thumbThicknessExpanded else style.metrics.thumbThickness
    val trackBackground = if (trackIsVisible) style.colors.trackBackground else Color.Transparent
    val trackPadding = if (trackIsVisible) style.metrics.trackPaddingExpanded else style.metrics.trackPadding
    ScrollbarImpl(
        adapter = adapter,
        modifier =
        modifier
            .alpha(animatedAlpha)
            .animateContentSize()
            .width(thumbWidth)
            .background(trackBackground)
            .padding(trackPadding)
            .scrollable(
                scrollState,
                orientation = Orientation.Vertical,
                reverseDirection = true,
            ).pointerInput(Unit) {
                detectTapGestures { offset ->
                    clickPosition = offset.y.toInt()
                }
            }.onSizeChanged {
                scrollbarWidth.value = it.width
                scrollbarHeight.value = it.height
            },
        reverseLayout = reverseLayout,
        style = style,
        interactionSource = interactionSource,
        isVertical = isVertical,
    )
}

@Composable
public fun VerticalScrollbar(
    adapter: ScrollbarAdapter,
    modifier: Modifier = Modifier,
    reverseLayout: Boolean = false,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
    style: ScrollbarStyle = JewelTheme.scrollbarStyle,
) {
    ScrollbarImpl(
        adapter = adapter,
        modifier = modifier,
        reverseLayout = reverseLayout,
        style = style,
        interactionSource = interactionSource,
        isVertical = true,
    )
}

@Composable
public fun HorizontalScrollbar(
    adapter: ScrollbarAdapter,
    modifier: Modifier = Modifier,
    reverseLayout: Boolean = false,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
    style: ScrollbarStyle = JewelTheme.scrollbarStyle,
) {
    ScrollbarImpl(
        adapter = adapter,
        modifier = modifier,
        reverseLayout = reverseLayout,
        style = style,
        interactionSource = interactionSource,
        isVertical = false,
    )
}

@Deprecated("Use HorizontalScrollbar with an appropriate style.")
@Composable
public fun TabStripHorizontalScrollbar(
    adapter: ScrollbarAdapter,
    style: ScrollbarStyle,
    modifier: Modifier = Modifier,
    reverseLayout: Boolean = false,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
) {
    HorizontalScrollbar(
        adapter = adapter,
        modifier = modifier.padding(1.dp),
        reverseLayout = reverseLayout,
        style = style,
        interactionSource = interactionSource,
    )
}

// ===========================================================================
// Note: most of the code below is copied and adapted from the stock scrollbar
// ===========================================================================

@Composable
private fun ScrollbarImpl(
    adapter: ScrollbarAdapter,
    reverseLayout: Boolean,
    style: ScrollbarStyle,
    interactionSource: MutableInteractionSource,
    isVertical: Boolean,
    modifier: Modifier = Modifier,
) {
    with(LocalDensity.current) {
        val dragInteraction = remember { mutableStateOf<DragInteraction.Start?>(null) }
        DisposableEffect(interactionSource) {
            onDispose {
                dragInteraction.value?.let { interaction ->
                    interactionSource.tryEmit(DragInteraction.Cancel(interaction))
                    dragInteraction.value = null
                }
            }
        }

        var containerSize by remember { mutableIntStateOf(0) }
        val isHovered by interactionSource.collectIsHoveredAsState()

        val isHighlighted by remember {
            derivedStateOf { isHovered || dragInteraction.value is DragInteraction.Start }
        }

        val thumbMinHeight = style.metrics.minThumbLength.toPx()

        val coroutineScope = rememberCoroutineScope()
        val sliderAdapter =
            remember(
                adapter,
                containerSize,
                thumbMinHeight,
                reverseLayout,
                isVertical,
                coroutineScope,
            ) {
                SliderAdapter(adapter, containerSize, thumbMinHeight, reverseLayout, isVertical, coroutineScope)
            }

        val thumbThickness = style.metrics.thumbThickness.roundToPx()
        val measurePolicy =
            if (isVertical) {
                remember(sliderAdapter, thumbThickness) {
                    verticalMeasurePolicy(sliderAdapter, { containerSize = it }, thumbThickness)
                }
            } else {
                remember(sliderAdapter, thumbThickness) {
                    horizontalMeasurePolicy(sliderAdapter, { containerSize = it }, thumbThickness)
                }
            }

        val targetColor = if (isHighlighted) {
            style.colors.thumbBackgroundHovered
        } else {
            style.colors.thumbBackground
        }
        val thumbColor = if (style.scrollbarVisibility is WhenScrolling) {
            val durationMillis = style.scrollbarVisibility.expandAnimationDuration.inWholeMilliseconds.toInt()
            animateColorAsState(
                targetValue = targetColor,
                animationSpec = tween(durationMillis),
            ).value
        } else {
            targetColor
        }
        val isVisible = sliderAdapter.thumbSize < containerSize

        Layout(
            {
                Box(
                    Modifier
                        .layoutId("thumb")
                        .thenIf(isVisible) {
                            background(
                                color = thumbColor,
                                shape = RoundedCornerShape(style.metrics.thumbCornerSize),
                            )
                        }.scrollbarDrag(
                            interactionSource = interactionSource,
                            draggedInteraction = dragInteraction,
                            sliderAdapter = sliderAdapter,
                        ),
                )
            },
            modifier
                .hoverable(interactionSource = interactionSource)
                .scrollOnPressTrack(isVertical, reverseLayout, sliderAdapter),
            measurePolicy,
        )
    }
}

private val SliderAdapter.thumbPixelRange: IntRange
    get() {
        val start = position.roundToInt()
        val endExclusive = start + thumbSize.roundToInt()

        return (start until endExclusive)
    }

private val IntRange.size get() = last + 1 - first

private fun verticalMeasurePolicy(
    sliderAdapter: SliderAdapter,
    setContainerSize: (Int) -> Unit,
    scrollThickness: Int,
) = MeasurePolicy { measurables, constraints ->
    setContainerSize(constraints.maxHeight)
    val pixelRange = sliderAdapter.thumbPixelRange
    val placeable =
        measurables.first().measure(
            Constraints.fixed(
                constraints.constrainWidth(scrollThickness),
                pixelRange.size,
            ),
        )
    layout(placeable.width, constraints.maxHeight) {
        placeable.place(0, pixelRange.first)
    }
}

private fun horizontalMeasurePolicy(
    sliderAdapter: SliderAdapter,
    setContainerSize: (Int) -> Unit,
    scrollThickness: Int,
) = MeasurePolicy { measurables, constraints ->
    setContainerSize(constraints.maxWidth)
    val pixelRange = sliderAdapter.thumbPixelRange
    val placeable =
        measurables.first().measure(
            Constraints.fixed(
                pixelRange.size,
                constraints.constrainHeight(scrollThickness),
            ),
        )
    layout(constraints.maxWidth, placeable.height) {
        placeable.place(pixelRange.first, 0)
    }
}

private fun Modifier.scrollbarDrag(
    interactionSource: MutableInteractionSource,
    draggedInteraction: MutableState<DragInteraction.Start?>,
    sliderAdapter: SliderAdapter,
): Modifier =
    composed {
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

private fun Modifier.scrollOnPressTrack(
    isVertical: Boolean,
    reverseLayout: Boolean,
    sliderAdapter: SliderAdapter,
) = composed {
    val coroutineScope = rememberCoroutineScope()
    val scroller =
        remember(sliderAdapter, coroutineScope, reverseLayout) {
            TrackPressScroller(coroutineScope, sliderAdapter, reverseLayout)
        }
    Modifier.pointerInput(scroller) {
        detectScrollViaTrackGestures(
            isVertical = isVertical,
            scroller = scroller,
        )
    }
}

/**
 * Responsible for scrolling when the scrollbar track is pressed (outside
 * the thumb).
 */
private class TrackPressScroller(
    private val coroutineScope: CoroutineScope,
    private val sliderAdapter: SliderAdapter,
    private val reverseLayout: Boolean,
) {
    /**
     * The current direction of scroll (1: down/right, -1: up/left, 0: not
     * scrolling)
     */
    private var direction = 0

    /** The currently pressed location (in pixels) on the scrollable axis. */
    private var offset: Float? = null

    /** The job that keeps scrolling while the track is pressed. */
    private var job: Job? = null

    /**
     * Calculates the direction of scrolling towards the given offset (in
     * pixels).
     */
    private fun directionOfScrollTowards(offset: Float): Int {
        val pixelRange = sliderAdapter.thumbPixelRange
        return when {
            offset < pixelRange.first -> if (reverseLayout) 1 else -1
            offset > pixelRange.last -> if (reverseLayout) -1 else 1
            else -> 0
        }
    }

    /**
     * Scrolls once towards the current offset, if it matches the direction of
     * the current gesture.
     */
    private suspend fun scrollTowardsCurrentOffset() {
        offset?.let {
            val currentDirection = directionOfScrollTowards(it)
            if (currentDirection != direction) {
                return
            }
            with(sliderAdapter.adapter) {
                scrollTo(scrollOffset + currentDirection * viewportSize)
            }
        }
    }

    /** Starts the job that scrolls continuously towards the current offset. */
    private fun startScrolling() {
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

        if (direction != 0) {
            startScrolling()
        }
    }

    /** Invoked when the pointer moves while pressed during the gesture. */
    fun onMovePressed(offset: Float) {
        this.offset = offset
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

    /** Invoked when the gesture is cancelled. */
    fun onGestureCancelled() {
        cleanupAfterGesture()
        // Maybe revert to the initial position?
    }
}

/**
 * Detects the pointer events relevant for the "scroll by pressing on the
 * track outside the thumb" gesture and calls the corresponding methods in
 * the [scroller].
 */
private suspend fun PointerInputScope.detectScrollViaTrackGestures(
    isVertical: Boolean,
    scroller: TrackPressScroller,
) {
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

/**
 * The delay between the 1st and 2nd scroll while the scrollbar track is
 * pressed outside the thumb.
 */
internal const val DELAY_BEFORE_SECOND_SCROLL_ON_TRACK_PRESS: Long = 300L

/**
 * The delay between each subsequent (after the 2nd) scroll while the
 * scrollbar track is pressed outside the thumb.
 */
internal const val DELAY_BETWEEN_SCROLLS_ON_TRACK_PRESS: Long = 100L

internal class SliderAdapter(
    val adapter: ScrollbarAdapter,
    private val trackSize: Int,
    private val minHeight: Float,
    private val reverseLayout: Boolean,
    private val isVertical: Boolean,
    private val coroutineScope: CoroutineScope,
) {
    private val contentSize get() = adapter.contentSize
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

    val bounds get() = position..position + thumbSize

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
                    (currentPosition + dragDelta + unscrolledDragDistance).coerceIn(
                        0.0,
                        maxScrollPosition,
                    )
                val sliderDelta = targetPosition - currentPosition

                // Have to add to position for smooth content scroll if the items are of different size
                val newPos = position + sliderDelta
                setPosition(newPos)
                unscrolledDragDistance += dragDelta - sliderDelta
            }
        }
    }
}
