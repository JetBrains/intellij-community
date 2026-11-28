// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jewel.ui.component

import androidx.compose.foundation.MutatePriority
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.DragInteraction
import androidx.compose.foundation.interaction.Interaction
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ScrollWheel
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import kotlin.math.sign
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.time.Duration
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.intui.standalone.theme.IntUiTheme
import org.jetbrains.jewel.ui.component.styling.ScrollbarStyle
import org.jetbrains.jewel.ui.component.styling.ScrollbarVisibility
import org.jetbrains.jewel.ui.component.styling.TrackClickBehavior
import org.jetbrains.jewel.ui.theme.scrollbarStyle
import org.junit.Rule
import org.junit.Test

/**
 * Where the thumb of a container scrollbar starts, which way a drag, a track click and the mouse wheel move it, and
 * whether a drag or a click can take over from a scroll in progress. Also, which thumbs take a drag at all.
 */
class ScrollbarDirectionTest {
    @get:Rule val rule = createComposeRule()

    @Test
    fun `the horizontal thumb starts at the left edge in a left-to-right layout`() {
        rule.setContent { HorizontalContainer(ScrollState(0), LayoutDirection.Ltr) }

        val (first, _) = assertNotNull(horizontalThumbBounds())
        assertTrue(first < containerWidthPx() / 2, "Expected the thumb at the left edge, but it starts at x=$first")
    }

    @Test
    fun `the horizontal thumb starts at the right edge in a right-to-left layout`() {
        rule.setContent { HorizontalContainer(ScrollState(0), LayoutDirection.Rtl) }

        val (first, _) = assertNotNull(horizontalThumbBounds())
        assertTrue(first > containerWidthPx() / 2, "Expected the thumb at the right edge, but it starts at x=$first")
    }

    @Test
    fun `a drag of the horizontal thumb to the left scrolls forward in a right-to-left layout`() {
        val state = ScrollState(0)
        rule.setContent { HorizontalContainer(state, LayoutDirection.Rtl) }
        val middle = scrollToMiddle(state)

        val (first, last) = assertNotNull(horizontalThumbBounds())
        val start = Offset((first + last) / 2f, trackY())
        rule.onNodeWithTag(CONTAINER_TAG).performMouseInput {
            moveTo(start)
            press()
            moveTo(start - Offset(40f, 0f))
            release()
        }
        rule.waitForIdle()

        assertTrue(state.value > middle, "Expected a forward scroll from $middle, but the value is ${state.value}")
    }

    @Test
    fun `a click on the horizontal track left of the thumb scrolls forward in a right-to-left layout`() {
        val state = ScrollState(0)
        rule.setContent { HorizontalContainer(state, LayoutDirection.Rtl, TrackClickBehavior.NextPage) }
        val middle = scrollToMiddle(state)

        rule.onNodeWithTag(CONTAINER_TAG).performMouseInput { click(Offset(6f, trackY())) }
        rule.waitForIdle()

        assertTrue(state.value > middle, "Expected a forward scroll from $middle, but the value is ${state.value}")
    }

    @Test
    fun `jump to spot on the horizontal track follows the click in a right-to-left layout`() {
        val state = ScrollState(0)
        rule.setContent { HorizontalContainer(state, LayoutDirection.Rtl, TrackClickBehavior.JumpToSpot) }
        rule.waitForIdle()

        // The far left of the track is the end of the content in a right-to-left layout.
        rule.onNodeWithTag(CONTAINER_TAG).performMouseInput { click(Offset(6f, trackY())) }
        rule.waitForIdle()

        assertTrue(state.value > state.maxValue / 2, "Expected a jump near the end, but the value is ${state.value}")
    }

    @Test
    fun `the wheel over the horizontal track scrolls like over the content in a right-to-left layout`() {
        val state = ScrollState(0)
        rule.setContent { HorizontalContainer(state, LayoutDirection.Rtl) }
        val width = containerWidthPx()

        val overContent = wheelDelta(state, Offset(width / 2f, 10f), ScrollWheel.Horizontal)
        val overTrack = wheelDelta(state, Offset(width / 2f, trackY()), ScrollWheel.Horizontal)

        assertTrue(overContent != 0, "The mouse wheel over the content did not scroll")
        assertEquals(overContent.sign, overTrack.sign, "Over the content: $overContent, over the track: $overTrack")
    }

    @Test
    fun `jump to spot on the vertical track follows the click in a reversed layout`() {
        val state = ScrollState(0)
        rule.setContent { ReversedVerticalContainer(state, TrackClickBehavior.JumpToSpot) }
        rule.waitForIdle()

        // The top of the track is the end of the content in a reversed layout.
        rule.onNodeWithTag(CONTAINER_TAG).performMouseInput { click(Offset(trackX(), 6f)) }
        rule.waitForIdle()

        assertTrue(state.value > state.maxValue / 2, "Expected a jump near the end, but the value is ${state.value}")
    }

    @Test
    fun `the wheel over the vertical track scrolls like over the content in a reversed layout`() {
        val state = ScrollState(0)
        rule.setContent { ReversedVerticalContainer(state) }

        val overContent = wheelDelta(state, Offset(10f, containerHeightPx() / 2f), ScrollWheel.Vertical)
        val overTrack = wheelDelta(state, Offset(trackX(), containerHeightPx() / 2f), ScrollWheel.Vertical)

        assertTrue(overContent != 0, "The mouse wheel over the content did not scroll")
        assertEquals(overContent.sign, overTrack.sign, "Over the content: $overContent, over the track: $overTrack")
    }

    @Test
    fun `a thumb drag takes over from a wheel scroll in progress`() {
        val state = ScrollState(0)
        lateinit var scope: CoroutineScope
        rule.setContent {
            scope = rememberCoroutineScope()
            HorizontalContainer(state, LayoutDirection.Ltr)
        }
        val middle = scrollToMiddle(state)
        val wheelScroll = startWheelPriorityScroll(state, scope)

        val (first, last) = assertNotNull(horizontalThumbBounds())
        val start = Offset((first + last) / 2f, trackY())
        rule.onNodeWithTag(CONTAINER_TAG).performMouseInput {
            moveTo(start)
            press()
            moveTo(start + Offset(40f, 0f))
            release()
        }
        rule.waitUntil("the wheel scroll in progress to stop") { wheelScroll.isCompleted }
        rule.waitForIdle()

        assertTrue(wheelScroll.isCancelled, "The wheel scroll is still running")
        assertTrue(state.value > middle, "Expected the drag to scroll from $middle, but the value is ${state.value}")
    }

    @Test
    fun `a track click takes over from a wheel scroll in progress`() {
        val state = ScrollState(0)
        lateinit var scope: CoroutineScope
        rule.setContent {
            scope = rememberCoroutineScope()
            HorizontalContainer(state, LayoutDirection.Ltr, TrackClickBehavior.NextPage)
        }
        val middle = scrollToMiddle(state)
        val wheelScroll = startWheelPriorityScroll(state, scope)

        rule.onNodeWithTag(CONTAINER_TAG).performMouseInput { click(Offset(width - 6f, trackY())) }
        rule.waitUntil("the wheel scroll in progress to stop") { wheelScroll.isCompleted }
        rule.waitForIdle()

        assertTrue(wheelScroll.isCancelled, "The wheel scroll is still running")
        assertTrue(state.value > middle, "Expected the click to scroll from $middle, but the value is ${state.value}")
    }

    @Test
    fun `a drag on a track shorter than the minimum thumb length does not scroll`() {
        val state = ScrollState(0)
        rule.setContent { HorizontalContainer(state, LayoutDirection.Ltr, width = 20.dp) }
        rule.waitForIdle()

        rule.onNodeWithTag(CONTAINER_TAG).performMouseInput {
            val start = Offset(width / 2f, trackY())
            moveTo(start)
            press()
            moveTo(start + Offset(8f, 0f))
            release()
        }
        rule.waitForIdle()

        assertEquals(0, state.value)
    }

    @Test
    fun `the thumb of a scrollbar that cannot scroll does not take a drag`() {
        val interactions = RecordingInteractionSource()
        rule.setContent {
            IntUiTheme {
                val state = rememberScrollState()
                Box(Modifier.size(CONTAINER_SIZE).background(CONTENT_COLOR).testTag(CONTAINER_TAG)) {
                    Box(Modifier.fillMaxSize().horizontalScroll(state)) { Box(Modifier.size(40.dp)) }
                    HorizontalScrollbar(
                        scrollState = state,
                        modifier = Modifier.align(Alignment.BottomStart).fillMaxWidth(),
                        interactionSource = interactions,
                        style = alwaysVisibleStyle(TrackClickBehavior.NextPage),
                    )
                }
            }
        }
        rule.waitForIdle()

        rule.onNodeWithTag(CONTAINER_TAG).performMouseInput {
            val start = Offset(width / 2f, trackY())
            moveTo(start)
            press()
            moveTo(start + Offset(40f, 0f))
            release()
        }
        rule.waitForIdle()

        assertEquals(emptyList(), interactions.emitted.filterIsInstance<DragInteraction>())
    }

    @Composable
    private fun HorizontalContainer(
        state: ScrollState,
        direction: LayoutDirection,
        clickBehavior: TrackClickBehavior = TrackClickBehavior.NextPage,
        width: Dp = CONTAINER_SIZE,
    ) {
        CompositionLocalProvider(LocalLayoutDirection provides direction) {
            IntUiTheme {
                HorizontallyScrollableContainer(
                    modifier = Modifier.size(width, CONTAINER_SIZE).background(CONTENT_COLOR).testTag(CONTAINER_TAG),
                    scrollState = state,
                    style = alwaysVisibleStyle(clickBehavior),
                ) {
                    Box(Modifier.size(CONTENT_LENGTH, 40.dp))
                }
            }
        }
    }

    @Composable
    private fun ReversedVerticalContainer(
        state: ScrollState,
        clickBehavior: TrackClickBehavior = TrackClickBehavior.NextPage,
    ) {
        IntUiTheme {
            VerticallyScrollableContainer(
                modifier = Modifier.size(CONTAINER_SIZE).background(CONTENT_COLOR).testTag(CONTAINER_TAG),
                scrollState = state,
                reverseLayout = true,
                style = alwaysVisibleStyle(clickBehavior),
            ) {
                Box(Modifier.size(40.dp, CONTENT_LENGTH))
            }
        }
    }

    private fun scrollToMiddle(state: ScrollState): Int {
        rule.waitForIdle()
        rule.runOnIdle { runBlocking { state.scrollTo(state.maxValue / 2) } }
        rule.waitForIdle()
        return state.value
    }

    /**
     * Starts a scroll at [MutatePriority.UserInput], the priority of the mouse wheel and the trackpad, that runs until
     * something cancels it. It stands in for the momentum of a trackpad gesture, and it runs on the UI thread in
     * [scope] like a real wheel scroll.
     */
    private fun startWheelPriorityScroll(state: ScrollState, scope: CoroutineScope): Job {
        lateinit var job: Job
        rule.runOnUiThread { job = scope.launch { state.scroll(MutatePriority.UserInput) { awaitCancellation() } } }
        rule.waitUntil { state.isScrollInProgress }
        return job
    }

    /** Scrolls the mouse wheel at [position] from the middle of the range, and returns the change of the value. */
    private fun wheelDelta(state: ScrollState, position: Offset, wheel: ScrollWheel): Int {
        val middle = scrollToMiddle(state)
        rule.onNodeWithTag(CONTAINER_TAG).performMouseInput {
            moveTo(position)
            scroll(3f, wheel)
        }
        rule.waitForIdle()
        return state.value - middle
    }

    /** The first and last x of the horizontal thumb, or null when nothing is drawn. */
    private fun horizontalThumbBounds(): Pair<Int, Int>? {
        rule.waitForIdle()
        val image = rule.onNodeWithTag(CONTAINER_TAG).captureToImage().toPixelMap()
        val y = trackY().toInt()
        val xs = (0 until image.width).filter { x -> image[x, y] != CONTENT_COLOR }
        return if (xs.isEmpty()) null else xs.first() to xs.last()
    }

    // A line through the middle of the thumb: the track is 14 dp thick and has 2 dp of padding.
    private fun trackY(): Float = containerHeightPx() - with(rule.density) { 7.dp.toPx() }

    private fun trackX(): Float = containerWidthPx() - with(rule.density) { 7.dp.toPx() }

    private fun containerWidthPx(): Int = rule.onNodeWithTag(CONTAINER_TAG).fetchSemanticsNode().size.width

    private fun containerHeightPx(): Int = rule.onNodeWithTag(CONTAINER_TAG).fetchSemanticsNode().size.height

    @Composable
    private fun alwaysVisibleStyle(clickBehavior: TrackClickBehavior): ScrollbarStyle =
        ScrollbarStyle(
            colors = JewelTheme.scrollbarStyle.colors,
            metrics = JewelTheme.scrollbarStyle.metrics,
            trackClickBehavior = clickBehavior,
            scrollbarVisibility =
                ScrollbarVisibility.AlwaysVisible(
                    trackThickness = 14.dp,
                    trackPadding = PaddingValues(2.dp),
                    trackPaddingWithBorder = PaddingValues(2.dp),
                    thumbColorAnimationDuration = Duration.ZERO,
                    trackColorAnimationDuration = Duration.ZERO,
                    scrollbarBackgroundColorLight = Color.Transparent,
                    scrollbarBackgroundColorDark = Color.Transparent,
                ),
        )

    /** Records each interaction that the scrollbar emits. */
    private class RecordingInteractionSource : MutableInteractionSource by MutableInteractionSource() {
        val emitted = mutableListOf<Interaction>()

        override suspend fun emit(interaction: Interaction) {
            emitted += interaction
        }

        override fun tryEmit(interaction: Interaction): Boolean {
            emitted += interaction
            return true
        }
    }

    private companion object {
        const val CONTAINER_TAG = "container"
        val CONTENT_COLOR = Color.White
        val CONTAINER_SIZE = 200.dp
        val CONTENT_LENGTH = 800.dp
    }
}
