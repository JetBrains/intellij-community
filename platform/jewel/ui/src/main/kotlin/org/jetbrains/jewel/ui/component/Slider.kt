package org.jetbrains.jewel.ui.component

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.TweenSpec
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.MutatePriority
import androidx.compose.foundation.MutatorMutex
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.DragScope
import androidx.compose.foundation.gestures.DraggableState
import androidx.compose.foundation.gestures.GestureCancellationException
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.DragInteraction
import androidx.compose.foundation.interaction.FocusInteraction
import androidx.compose.foundation.interaction.HoverInteraction
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSizeIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.progressSemantics
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.lerp
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.nativeKeyCode
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.debugInspectorInfo
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import org.jetbrains.jewel.foundation.Stroke
import org.jetbrains.jewel.foundation.modifier.border
import org.jetbrains.jewel.foundation.state.CommonStateBitMask.Active
import org.jetbrains.jewel.foundation.state.CommonStateBitMask.Enabled
import org.jetbrains.jewel.foundation.state.CommonStateBitMask.Focused
import org.jetbrains.jewel.foundation.state.CommonStateBitMask.Hovered
import org.jetbrains.jewel.foundation.state.CommonStateBitMask.Pressed
import org.jetbrains.jewel.foundation.state.FocusableComponentState
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.component.styling.SliderStyle
import org.jetbrains.jewel.ui.focusOutline
import org.jetbrains.jewel.ui.theme.sliderStyle

/**
 * A slider component that follows the standard visual styling.
 *
 * Provides a draggable thumb that moves along a track to select a value from a continuous range. Supports step
 * intervals, keyboard navigation, and both mouse and touch input. The component includes visual feedback for hover,
 * focus, and press states.
 *
 * Features:
 * - Continuous or stepped value selection
 * - Keyboard navigation with arrow keys, Home/End, and Page Up/Down
 * - Mouse drag and click positioning
 * - Touch input support
 * - Optional tick marks for stepped values
 * - RTL layout support
 *
 * This implementation is heavily based on the Material 2 slider implementation.
 *
 * **Usage example:**
 * [`Slider.kt`](https://github.com/JetBrains/intellij-community/blob/master/platform/jewel/samples/showcase/src/main/kotlin/org/jetbrains/jewel/samples/showcase/components/Slider.kt)
 *
 * **Swing equivalent:** [`JSlider`](https://docs.oracle.com/javase/tutorial/uiswing/components/slider.html)
 *
 * @param value The current value of the slider
 * @param onValueChange Called when the value changes as the user drags the thumb
 * @param modifier Modifier to be applied to the slider
 * @param enabled Controls whether the slider can be interacted with
 * @param valueRange The range of values that the slider can take
 * @param steps The number of discrete steps between valueRange.start and valueRange.endInclusive
 * @param onValueChangeFinished Called when the user finishes moving the slider
 * @param interactionSource Source of interactions for this slider
 * @param style The visual styling configuration for the slider
 * @see javax.swing.JSlider
 */
@Composable
public fun Slider(
    value: Float,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    valueRange: ClosedFloatingPointRange<Float> = 0f..1f,
    steps: Int = 0,
    onValueChangeFinished: (() -> Unit)? = null,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
    style: SliderStyle = JewelTheme.sliderStyle,
) {
    require(steps >= 0) { "The number of steps must be >= 0" }

    val onValueChangeState = rememberUpdatedState(onValueChange)
    val onValueChangeFinishedState = rememberUpdatedState(onValueChangeFinished)
    val tickFractions = remember(steps) { stepsToTickFractions(steps) }
    val isRtl = LocalLayoutDirection.current == LayoutDirection.Rtl

    val focusRequester = remember { FocusRequester() }
    val thumbSize = style.metrics.thumbSize
    val ticksHeight =
        if (tickFractions.isNotEmpty()) {
            style.metrics.trackToStepSpacing + style.metrics.stepLineHeight
        } else {
            0.dp
        }

    val minHeight = thumbSize.height + style.metrics.thumbBorderWidth * 2 + ticksHeight

    BoxWithConstraints(
        modifier
            .requiredSizeIn(minWidth = thumbSize.height * 2, minHeight = minHeight)
            .sliderSemantics(value, enabled, onValueChange, onValueChangeFinished, valueRange, steps)
            .focusRequester(focusRequester)
            .focusable(enabled, interactionSource)
            .slideOnKeyEvents(enabled, steps, valueRange, value, isRtl, onValueChangeState, onValueChangeFinishedState)
    ) {
        val widthPx = constraints.maxWidth.toFloat()
        val maxPx: Float
        val minPx: Float

        with(LocalDensity.current) {
            maxPx = max(widthPx - thumbSize.width.toPx(), 0f)
            minPx = min(thumbSize.width.toPx(), maxPx)
        }

        fun scaleToUserValue(offset: Float) = scale(minPx, maxPx, offset, valueRange.start, valueRange.endInclusive)

        fun scaleToOffset(userValue: Float) = scale(valueRange.start, valueRange.endInclusive, userValue, minPx, maxPx)

        val scope = rememberCoroutineScope()
        val rawOffset = remember { mutableFloatStateOf(scaleToOffset(value)) }
        val pressOffset = remember { mutableFloatStateOf(0f) }

        val draggableState =
            remember(minPx, maxPx, valueRange) {
                SliderDraggableState {
                    rawOffset.floatValue = (rawOffset.floatValue + it + pressOffset.floatValue)
                    pressOffset.floatValue = 0f
                    val offsetInTrack = rawOffset.floatValue.coerceIn(minPx, maxPx)
                    onValueChangeState.value.invoke(scaleToUserValue(offsetInTrack))
                }
            }

        CorrectValueSideEffect(::scaleToOffset, valueRange, minPx..maxPx, rawOffset, value)

        val canAnimate = !JewelTheme.isSwingCompatMode
        val gestureEndAction =
            rememberUpdatedState<(Float) -> Unit> { velocity: Float ->
                val current = rawOffset.floatValue
                val target = snapValueToTick(current, tickFractions, minPx, maxPx)
                focusRequester.requestFocus()
                if (current != target) {
                    scope.launch {
                        if (canAnimate) {
                            animateToTarget(draggableState, current, target, velocity)
                        } else {
                            draggableState.drag { dragBy(target - current) }
                        }
                        onValueChangeFinished?.invoke()
                    }
                } else if (!draggableState.isDragging) {
                    // check ifDragging in case the change is still in progress (touch -> drag case)
                    onValueChangeFinished?.invoke()
                }
            }
        val press =
            Modifier.sliderTapModifier(
                draggableState,
                interactionSource,
                widthPx,
                isRtl,
                rawOffset,
                gestureEndAction,
                pressOffset,
                enabled,
            )

        val drag =
            Modifier.draggable(
                orientation = Orientation.Horizontal,
                reverseDirection = isRtl,
                enabled = enabled,
                interactionSource = interactionSource,
                onDragStopped = { velocity -> gestureEndAction.value.invoke(velocity) },
                startDragImmediately = draggableState.isDragging,
                state = draggableState,
            )

        val coerced = value.coerceIn(valueRange.start, valueRange.endInclusive)
        val fraction = calcFraction(valueRange.start, valueRange.endInclusive, coerced)
        SliderImpl(
            enabled = enabled,
            positionFraction = fraction,
            tickFractions = tickFractions,
            style = style,
            minHeight = minHeight,
            width = maxPx - minPx,
            interactionSource = interactionSource,
            modifier = press.then(drag),
        )
    }
}

private val SliderMinWidth = 100.dp // Completely arbitrary

@Composable
private fun SliderImpl(
    enabled: Boolean,
    positionFraction: Float,
    tickFractions: List<Float>,
    style: SliderStyle,
    width: Float,
    minHeight: Dp,
    interactionSource: MutableInteractionSource,
    modifier: Modifier = Modifier,
) {
    Box(modifier.then(Modifier.widthIn(min = SliderMinWidth).heightIn(min = minHeight))) {
        val widthDp: Dp
        with(LocalDensity.current) { widthDp = width.toDp() }

        Track(
            style = style,
            enabled = enabled,
            positionFractionEnd = positionFraction,
            tickFractions = tickFractions,
            modifier = Modifier.fillMaxWidth(),
        )

        val offset = (widthDp + style.metrics.thumbSize.width) * positionFraction
        SliderThumb(offset, interactionSource, style, enabled)
    }
}

@Composable
private fun Track(
    style: SliderStyle,
    enabled: Boolean,
    positionFractionEnd: Float,
    tickFractions: List<Float>,
    modifier: Modifier = Modifier,
) {
    val trackStrokeWidthPx: Float
    val thumbWidthPx: Float
    val trackYPx: Float
    val stepLineHeightPx: Float
    val stepLineWidthPx: Float
    val trackToMarkersGapPx: Float

    with(LocalDensity.current) {
        trackStrokeWidthPx = style.metrics.trackHeight.toPx()
        thumbWidthPx = style.metrics.thumbSize.width.toPx()
        trackYPx = style.metrics.thumbSize.width.toPx() / 2 + style.metrics.thumbBorderWidth.toPx()
        stepLineHeightPx = style.metrics.stepLineHeight.toPx()
        stepLineWidthPx = style.metrics.stepLineWidth.toPx()
        trackToMarkersGapPx = style.metrics.trackToStepSpacing.toPx()
    }

    Canvas(modifier) {
        val isRtl = layoutDirection == LayoutDirection.Rtl

        val trackLeft = Offset(thumbWidthPx / 2, trackYPx)
        val trackRight = Offset(size.width - thumbWidthPx / 2, trackYPx)
        val trackStart = if (isRtl) trackRight else trackLeft
        val trackEnd = if (isRtl) trackLeft else trackRight

        drawLine(
            color = if (enabled) style.colors.track else style.colors.trackDisabled,
            start = trackStart,
            end = trackEnd,
            strokeWidth = trackStrokeWidthPx,
            cap = StrokeCap.Round,
        )

        val activeTrackStart = Offset(trackStart.x, trackYPx)
        val activeTrackEnd =
            Offset(
                x = trackStart.x + (trackEnd.x - trackStart.x) * positionFractionEnd - thumbWidthPx / 2,
                y = trackYPx,
            )

        drawLine(
            color = if (enabled) style.colors.trackFilled else style.colors.trackFilledDisabled,
            start = activeTrackStart,
            end = activeTrackEnd,
            strokeWidth = trackStrokeWidthPx,
            cap = StrokeCap.Round,
        )

        val stepMarkerY = trackStrokeWidthPx + trackToMarkersGapPx

        tickFractions.forEach { fraction ->
            drawLine(
                color = style.colors.stepMarker,
                start = Offset(lerp(trackStart, trackEnd, fraction).x, stepMarkerY),
                end = Offset(lerp(trackStart, trackEnd, fraction).x, stepMarkerY + stepLineHeightPx),
                strokeWidth = stepLineWidthPx,
                cap = StrokeCap.Round,
            )
        }
    }
}

@Suppress("ModifierNaming")
@Composable
private fun BoxScope.SliderThumb(
    offset: Dp,
    interactionSource: MutableInteractionSource,
    style: SliderStyle,
    enabled: Boolean,
    thumbModifier: Modifier = Modifier,
) {
    Box(Modifier.padding(start = offset, top = style.metrics.thumbBorderWidth).align(Alignment.TopStart)) {
        var state by remember { mutableStateOf(SliderState.of(enabled)) }
        remember(enabled) { state = state.copy(enabled = enabled) }

        LaunchedEffect(interactionSource) {
            interactionSource.interactions.collect { interaction ->
                when (interaction) {
                    is PressInteraction.Press,
                    is DragInteraction.Start -> state = state.copy(pressed = true)

                    is PressInteraction.Release,
                    is PressInteraction.Cancel,
                    is DragInteraction.Stop,
                    is DragInteraction.Cancel -> state = state.copy(pressed = false)

                    is HoverInteraction.Enter -> state = state.copy(hovered = true)
                    is HoverInteraction.Exit -> state = state.copy(hovered = false)
                    is FocusInteraction.Focus -> state = state.copy(focused = true)
                    is FocusInteraction.Unfocus -> state = state.copy(focused = false)
                }
            }
        }

        val thumbSize = style.metrics.thumbSize
        Spacer(
            thumbModifier
                .size(thumbSize)
                .hoverable(interactionSource, enabled)
                .background(style.colors.thumbFillFor(state).value, style.thumbShape)
                .border(
                    alignment = Stroke.Alignment.Outside,
                    width = style.metrics.thumbBorderWidth,
                    color = style.colors.thumbBorderFor(state).value,
                    shape = style.thumbShape,
                )
                .focusOutline(state, style.thumbShape)
        )
    }
}

private fun snapValueToTick(current: Float, tickFractions: List<Float>, minPx: Float, maxPx: Float): Float =
    // target is a closest anchor to the `current`, if exists
    tickFractions.minByOrNull { abs(lerp(minPx, maxPx, it) - current) }?.run { lerp(minPx, maxPx, this) } ?: current

private fun stepsToTickFractions(steps: Int): List<Float> =
    if (steps == 0) emptyList() else List(steps + 2) { it.toFloat() / (steps + 1) }

// Scale x1 from a1..b1 range to a2..b2 range
private fun scale(a1: Float, b1: Float, x1: Float, a2: Float, b2: Float) = lerp(a2, b2, calcFraction(a1, b1, x1))

// Calculate the 0..1 fraction that `pos` value represents between `a` and `b`
private fun calcFraction(a: Float, b: Float, pos: Float) =
    (if (b - a == 0f) 0f else (pos - a) / (b - a)).coerceIn(0f, 1f)

@Suppress("MutableStateParam") // To fix in JEWEL-922
@Composable
private fun CorrectValueSideEffect(
    scaleToOffset: (Float) -> Float,
    valueRange: ClosedFloatingPointRange<Float>,
    trackRange: ClosedFloatingPointRange<Float>,
    valueState: MutableState<Float>,
    value: Float,
) {
    SideEffect {
        val error = (valueRange.endInclusive - valueRange.start) / 1000
        val newOffset = scaleToOffset(value)
        if (abs(newOffset - valueState.value) > error && valueState.value in trackRange) {
            valueState.value = newOffset
        }
    }
}

private fun Modifier.sliderSemantics(
    value: Float,
    enabled: Boolean,
    onValueChange: (Float) -> Unit,
    onValueChangeFinished: (() -> Unit)? = null,
    valueRange: ClosedFloatingPointRange<Float> = 0f..1f,
    steps: Int = 0,
): Modifier {
    val coerced = value.coerceIn(valueRange.start, valueRange.endInclusive)

    return semantics {
            if (!enabled) disabled()

            setProgress(
                action = { targetValue ->
                    var newValue = targetValue.coerceIn(valueRange.start, valueRange.endInclusive)
                    val originalVal = newValue
                    val resolvedValue =
                        if (steps > 0) {
                            var distance: Float = newValue
                            for (i in 0..steps + 1) {
                                val stepValue =
                                    lerp(valueRange.start, valueRange.endInclusive, i.toFloat() / (steps + 1))
                                if (abs(stepValue - originalVal) <= distance) {
                                    distance = abs(stepValue - originalVal)
                                    newValue = stepValue
                                }
                            }
                            newValue
                        } else {
                            newValue
                        }
                    // This is to keep it consistent with AbsSeekbar.java: return false if no
                    // change from current.
                    if (resolvedValue == coerced) {
                        false
                    } else {
                        onValueChange(resolvedValue)
                        onValueChangeFinished?.invoke()
                        true
                    }
                }
            )
        }
        .progressSemantics(value, valueRange, steps)
}

@Suppress("ModifierComposed") // To fix in JEWEL-921
private fun Modifier.sliderTapModifier(
    draggableState: DraggableState,
    interactionSource: MutableInteractionSource,
    maxPx: Float,
    isRtl: Boolean,
    rawOffset: State<Float>,
    gestureEndAction: State<(Float) -> Unit>,
    pressOffset: MutableState<Float>,
    enabled: Boolean,
) =
    composed(
        factory = {
            if (enabled) {
                val scope = rememberCoroutineScope()
                pointerInput(draggableState, interactionSource, maxPx, isRtl) {
                    detectTapGestures(
                        onPress = { pos ->
                            val to = if (isRtl) maxPx - pos.x else pos.x
                            pressOffset.value = to - rawOffset.value
                            try {
                                awaitRelease()
                            } catch (_: GestureCancellationException) {
                                pressOffset.value = 0f
                            }
                        },
                        onTap = {
                            scope.launch {
                                draggableState.drag(MutatePriority.UserInput) {
                                    // just trigger animation, press offset will be applied
                                    dragBy(0f)
                                }
                                gestureEndAction.value.invoke(0f)
                            }
                        },
                    )
                }
            } else {
                this
            }
        },
        inspectorInfo =
            debugInspectorInfo {
                name = "sliderTapModifier"
                properties["draggableState"] = draggableState
                properties["interactionSource"] = interactionSource
                properties["maxPx"] = maxPx
                properties["isRtl"] = isRtl
                properties["rawOffset"] = rawOffset
                properties["gestureEndAction"] = gestureEndAction
                properties["pressOffset"] = pressOffset
                properties["enabled"] = enabled
            },
    )

private val SliderToTickAnimation = TweenSpec<Float>(durationMillis = 100)

private suspend fun animateToTarget(draggableState: DraggableState, current: Float, target: Float, velocity: Float) {
    draggableState.drag {
        var latestValue = current
        Animatable(initialValue = current).animateTo(target, SliderToTickAnimation, velocity) {
            dragBy(this.value - latestValue)
            @Suppress("AssignedValueIsNeverRead")
            latestValue = this.value
        }
    }
}

// TODO: Edge case - losing focus on slider while key is pressed will end up with
// onValueChangeFinished not being invoked
@OptIn(ExperimentalComposeUiApi::class)
private fun Modifier.slideOnKeyEvents(
    enabled: Boolean,
    steps: Int,
    valueRange: ClosedFloatingPointRange<Float>,
    value: Float,
    isRtl: Boolean,
    onValueChangeState: State<(Float) -> Unit>,
    onValueChangeFinishedState: State<(() -> Unit)?>,
): Modifier {
    require(steps >= 0) { "steps should be >= 0" }

    return this.onKeyEvent {
        if (!enabled) return@onKeyEvent false

        when (it.type) {
            KeyEventType.KeyDown -> {
                val rangeLength = abs(valueRange.endInclusive - valueRange.start)
                // When steps == 0, it means that a user is not limited by a step length (delta)
                // when using touch or mouse.
                // But it is not possible to adjust the value continuously when using keyboard
                // buttons -
                // the delta has to be discrete. In this case, 1% of the valueRange seems to make
                // sense.
                val actualSteps = if (steps > 0) steps + 1 else 100
                val delta = rangeLength / actualSteps
                when {
                    it.isDirectionUp -> {
                        onValueChangeState.value((value + delta).coerceIn(valueRange))
                        true
                    }

                    it.isDirectionDown -> {
                        onValueChangeState.value((value - delta).coerceIn(valueRange))
                        true
                    }

                    it.isDirectionRight -> {
                        val sign = if (isRtl) -1 else 1
                        onValueChangeState.value((value + sign * delta).coerceIn(valueRange))
                        true
                    }

                    it.isDirectionLeft -> {
                        val sign = if (isRtl) -1 else 1
                        onValueChangeState.value((value - sign * delta).coerceIn(valueRange))
                        true
                    }

                    it.isHome -> {
                        onValueChangeState.value(valueRange.start)
                        true
                    }

                    it.isMoveEnd -> {
                        onValueChangeState.value(valueRange.endInclusive)
                        true
                    }

                    it.isPgUp -> {
                        val page = (actualSteps / 10).coerceIn(1, 10)
                        onValueChangeState.value((value - page * delta).coerceIn(valueRange))
                        true
                    }

                    it.isPgDn -> {
                        val page = (actualSteps / 10).coerceIn(1, 10)
                        onValueChangeState.value((value + page * delta).coerceIn(valueRange))
                        true
                    }

                    else -> false
                }
            }

            KeyEventType.KeyUp -> {
                @Suppress("ComplexCondition") // In original m2 code
                if (
                    it.isDirectionDown ||
                        it.isDirectionUp ||
                        it.isDirectionRight ||
                        it.isDirectionLeft ||
                        it.isHome ||
                        it.isMoveEnd ||
                        it.isPgUp ||
                        it.isPgDn
                ) {
                    onValueChangeFinishedState.value?.invoke()
                    true
                } else {
                    false
                }
            }

            else -> false
        }
    }
}

internal val KeyEvent.isDirectionUp: Boolean
    get() = key.nativeKeyCode == java.awt.event.KeyEvent.VK_UP

internal val KeyEvent.isDirectionDown: Boolean
    get() = key.nativeKeyCode == java.awt.event.KeyEvent.VK_DOWN

internal val KeyEvent.isDirectionRight: Boolean
    get() = key.nativeKeyCode == java.awt.event.KeyEvent.VK_RIGHT

internal val KeyEvent.isDirectionLeft: Boolean
    get() = key.nativeKeyCode == java.awt.event.KeyEvent.VK_LEFT

internal val KeyEvent.isHome: Boolean
    get() = key.nativeKeyCode == java.awt.event.KeyEvent.VK_HOME

internal val KeyEvent.isMoveEnd: Boolean
    get() = key.nativeKeyCode == java.awt.event.KeyEvent.VK_END

internal val KeyEvent.isPgUp: Boolean
    get() = key.nativeKeyCode == java.awt.event.KeyEvent.VK_PAGE_UP

internal val KeyEvent.isPgDn: Boolean
    get() = key.nativeKeyCode == java.awt.event.KeyEvent.VK_PAGE_DOWN

internal fun lerp(start: Float, stop: Float, fraction: Float): Float = (1 - fraction) * start + fraction * stop

@Immutable
@JvmInline
public value class SliderState(public val state: ULong) : FocusableComponentState {
    override val isActive: Boolean
        get() = state and Active != 0UL

    override val isEnabled: Boolean
        get() = state and Enabled != 0UL

    override val isFocused: Boolean
        get() = state and Focused != 0UL

    override val isHovered: Boolean
        get() = state and Hovered != 0UL

    override val isPressed: Boolean
        get() = state and Pressed != 0UL

    public fun copy(
        enabled: Boolean = isEnabled,
        focused: Boolean = isFocused,
        pressed: Boolean = isPressed,
        hovered: Boolean = isHovered,
        active: Boolean = isActive,
    ): SliderState = of(enabled = enabled, focused = focused, pressed = pressed, hovered = hovered, active = active)

    override fun toString(): String =
        "${javaClass.simpleName}(isEnabled=$isEnabled, isFocused=$isFocused, isHovered=$isHovered, " +
            "isPressed=$isPressed, isActive=$isActive)"

    public companion object {
        public fun of(
            enabled: Boolean = true,
            focused: Boolean = false,
            pressed: Boolean = false,
            hovered: Boolean = false,
            active: Boolean = true,
        ): SliderState =
            SliderState(
                (if (enabled) Enabled else 0UL) or
                    (if (focused) Focused else 0UL) or
                    (if (hovered) Hovered else 0UL) or
                    (if (pressed) Pressed else 0UL) or
                    (if (active) Active else 0UL)
            )
    }
}

private class SliderDraggableState(val onDelta: (Float) -> Unit) : DraggableState {
    var isDragging by mutableStateOf(false)
        private set

    private val dragScope: DragScope =
        object : DragScope {
            override fun dragBy(pixels: Float) {
                onDelta(pixels)
            }
        }

    private val scrollMutex = MutatorMutex()

    override suspend fun drag(dragPriority: MutatePriority, block: suspend DragScope.() -> Unit) {
        coroutineScope {
            isDragging = true
            scrollMutex.mutateWith(dragScope, dragPriority, block)
            isDragging = false
        }
    }

    override fun dispatchRawDelta(delta: Float) {
        onDelta(delta)
    }
}
