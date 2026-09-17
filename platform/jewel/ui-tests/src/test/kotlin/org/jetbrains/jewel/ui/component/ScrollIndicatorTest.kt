// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jewel.ui.component

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.MouseButton
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.runBlocking
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.intui.standalone.styling.macOs
import org.jetbrains.jewel.intui.standalone.styling.windowsAndLinux
import org.jetbrains.jewel.intui.standalone.styling.windowsAndLinuxDark
import org.jetbrains.jewel.intui.standalone.styling.windowsAndLinuxLight
import org.jetbrains.jewel.intui.standalone.theme.IntUiTheme
import org.jetbrains.jewel.ui.component.styling.ScrollbarStyle
import org.jetbrains.jewel.ui.component.styling.ScrollbarVisibility
import org.jetbrains.jewel.ui.component.styling.ScrollbarVisibility.WhenScrolling
import org.jetbrains.jewel.ui.component.styling.TrackClickBehavior
import org.jetbrains.jewel.ui.theme.scrollbarStyle
import org.jetbrains.skiko.OS
import org.jetbrains.skiko.hostOs
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test

@Suppress("LargeClass") // Big test suite is big
class ScrollIndicatorTest {
    @get:Rule val rule = createComposeRule()

    @Test
    fun `draws the indicator over always-visible scrollable content`() {
        lateinit var scrollState: ScrollState

        rule.setContent {
            IntUiTheme {
                val state = rememberScrollState()
                scrollState = state
                AlwaysVisibleIndicator(state)
            }
        }
        rule.waitForIdle()

        assertTrue(scrollState.canScrollForward, "The content should be scrollable")
        assertTrue(hasIndicatorPixels(), "The indicator should paint over the content")
    }

    @Test
    fun `draws nothing when the content is not scrollable`() {
        rule.setContent {
            IntUiTheme {
                val state = rememberScrollState()
                val style = alwaysVisibleStyle()
                Box(
                    Modifier.size(CONTAINER_SIZE)
                        .background(CONTENT_COLOR)
                        .testTag(TAG)
                        .scrollIndicator(state, style = style)
                ) {
                    Box(Modifier.fillMaxWidth().height(20.dp).verticalScroll(state))
                }
            }
        }
        rule.waitForIdle()

        assertFalse(hasIndicatorPixels(), "No indicator should be drawn when the content fits")
    }

    @Test
    fun `dragging the thumb scrolls the content`() {
        lateinit var scrollState: ScrollState

        rule.setContent {
            IntUiTheme {
                val state = rememberScrollState()
                scrollState = state
                AlwaysVisibleIndicator(state)
            }
        }
        rule.waitForIdle()
        assertEquals(0, scrollState.value, "The content should start unscrolled")

        val width = rule.onNodeWithTag(TAG).fetchSemanticsNode().size.width
        rule.onNodeWithTag(TAG).performMouseInput {
            val trackX = width - 4f
            moveTo(Offset(trackX, 8f))
            press()
            moveTo(Offset(trackX, 60f))
            release()
        }
        rule.waitForIdle()

        assertTrue(scrollState.value > 0, "Dragging the thumb down should scroll the content down")
    }

    @Test
    fun `clicking the track jumps to the clicked spot`() {
        lateinit var scrollState: ScrollState

        rule.setContent {
            IntUiTheme {
                val state = rememberScrollState()
                scrollState = state
                AlwaysVisibleIndicator(state, clickBehavior = TrackClickBehavior.JumpToSpot)
            }
        }
        rule.waitForIdle()

        val size = rule.onNodeWithTag(TAG).fetchSemanticsNode().size
        rule.onNodeWithTag(TAG).performMouseInput {
            moveTo(Offset(size.width - 4f, size.height - 6f))
            press()
            release()
        }
        // The jump is instant, so that it cannot fight the paging repeat.
        rule.mainClock.advanceTimeBy(1_000)
        rule.waitForIdle()

        assertTrue(
            scrollState.value > 0,
            "Clicking near the track end should scroll towards the end. " + "Actual scroll offset: ${scrollState.value}",
        )
    }

    @Test
    fun `a press on the content does not reach the indicator`() {
        var contentPresses = 0
        lateinit var scrollState: ScrollState

        rule.setContent {
            IntUiTheme {
                val state = rememberScrollState()
                scrollState = state
                val style = alwaysVisibleStyle()
                Box(Modifier.size(CONTAINER_SIZE).testTag(TAG).scrollIndicator(state, style = style)) {
                    Box(Modifier.fillMaxWidth().verticalScroll(state).countPresses { contentPresses++ }) {
                        TallContent()
                    }
                }
            }
        }
        rule.waitForIdle()

        rule.onNodeWithTag(TAG).performMouseInput {
            moveTo(Offset(10f, 10f))
            press()
            release()
        }
        rule.waitForIdle()

        assertEquals(1, contentPresses, "Presses away from the track must reach the content")
        assertEquals(0, scrollState.value, "Pressing the content must not scroll via the indicator")
    }

    @Test
    fun `reverse layout parks the thumb at the end of the track`() {
        rule.setContent {
            IntUiTheme {
                val state = rememberLazyListState()
                val style = alwaysVisibleStyle()
                Box(
                    Modifier.size(CONTAINER_SIZE)
                        .background(CONTENT_COLOR)
                        .testTag(TAG)
                        .scrollIndicator(state, style = style, reverseLayout = true)
                ) {
                    LazyColumn(Modifier.fillMaxWidth(), state = state, reverseLayout = true) {
                        items(30) { Box(Modifier.fillMaxWidth().height(20.dp).background(CONTENT_COLOR)) }
                    }
                }
            }
        }
        rule.waitForIdle()

        // With reverseLayout, an unscrolled list parks the thumb at the bottom of the track.
        val image = rule.onNodeWithTag(TAG).captureToImage().toPixelMap()
        val trackX = image.width - 4
        assertTrue(image[trackX, image.height - 8] != CONTENT_COLOR, "The thumb should sit at the track end")
        assertTrue(image[trackX, 8] == CONTENT_COLOR, "The thumb should not be at the track start")
    }

    @Test
    fun `works with a lazy list state`() {
        lateinit var listState: LazyListState

        rule.setContent {
            IntUiTheme {
                val state = rememberLazyListState()
                listState = state
                val style = alwaysVisibleStyle()
                Box(
                    Modifier.size(CONTAINER_SIZE)
                        .background(CONTENT_COLOR)
                        .testTag(TAG)
                        .scrollIndicator(state, style = style)
                ) {
                    LazyColumn(Modifier.fillMaxWidth(), state = state) {
                        items(50) { Box(Modifier.fillMaxWidth().height(20.dp).background(CONTENT_COLOR)) }
                    }
                }
            }
        }
        rule.waitForIdle()

        assertTrue(listState.canScrollForward, "The lazy list should be scrollable")
        assertTrue(hasIndicatorPixels(), "The indicator should draw for lazy layouts too")
    }

    @Test
    fun `stays hidden until hovered when only visible while scrolling`() {
        rule.setContent {
            IntUiTheme {
                val state = rememberScrollState()
                val style = whenScrollingStyle()
                Box(
                    Modifier.size(CONTAINER_SIZE)
                        .background(CONTENT_COLOR)
                        .testTag(TAG)
                        .scrollIndicator(state, style = style)
                ) {
                    Box(Modifier.fillMaxWidth().verticalScroll(state)) { TallContent() }
                }
            }
        }
        rule.waitForIdle()

        assertFalse(hasIndicatorPixels(), "The indicator should be hidden at rest")

        val width = rule.onNodeWithTag(TAG).fetchSemanticsNode().size.width
        rule.onNodeWithTag(TAG).performMouseInput {
            enter(Offset(width - 4f, 40f))
            moveTo(Offset(width - 4f, 42f))
        }
        rule.waitForIdle()

        assertTrue(hasIndicatorPixels(), "Hovering the track should reveal the indicator")
    }

    @Test
    fun `disabling the indicator stops it handling clicks`() {
        lateinit var scrollState: ScrollState

        rule.setContent {
            IntUiTheme {
                val state = rememberScrollState()
                scrollState = state
                AlwaysVisibleIndicator(state, enabled = false)
            }
        }
        rule.waitForIdle()

        val size = rule.onNodeWithTag(TAG).fetchSemanticsNode().size
        rule.onNodeWithTag(TAG).performMouseInput {
            moveTo(Offset(size.width - 4f, size.height - 6f))
            press()
            release()
        }
        rule.waitForIdle()

        assertEquals(0, scrollState.value, "A disabled indicator must not scroll the content")
    }

    @Test
    fun `a disabled indicator is still drawn`() {
        rule.setContent {
            IntUiTheme {
                val state = rememberScrollState()
                AlwaysVisibleIndicator(state, enabled = false)
            }
        }
        rule.waitForIdle()

        assertTrue(hasIndicatorPixels(), "A disabled indicator must still paint")
    }

    @Test
    fun `a disabled WhenScrolling indicator does not appear on hover`() {
        rule.setContent {
            IntUiTheme {
                val state = rememberScrollState()
                val style = whenScrollingStyle()
                Box(
                    Modifier.size(CONTAINER_SIZE)
                        .background(CONTENT_COLOR)
                        .testTag(TAG)
                        .scrollIndicator(state, style = style, enabled = false)
                ) {
                    Box(Modifier.fillMaxWidth().verticalScroll(state)) { TallContent() }
                }
            }
        }
        rule.waitForIdle()

        val width = rule.onNodeWithTag(TAG).fetchSemanticsNode().size.width
        rule.onNodeWithTag(TAG).performMouseInput {
            enter(Offset(width - 4f, 40f))
            moveTo(Offset(width - 4f, 42f))
        }
        rule.waitForIdle()

        assertFalse(hasIndicatorPixels(), "A disabled indicator must not reveal itself on hover")
    }

    @Test
    fun `a disabled indicator does not expand on hover`() {
        lateinit var scrollState: ScrollState

        rule.setContent {
            IntUiTheme {
                val state = rememberScrollState()
                scrollState = state
                val base = JewelTheme.scrollbarStyle
                val style =
                    ScrollbarStyle(
                        colors = base.colors,
                        metrics = base.metrics,
                        trackClickBehavior = TrackClickBehavior.JumpToSpot,
                        scrollbarVisibility =
                            ScrollbarVisibility.AlwaysVisible(
                                trackThickness = 8.dp,
                                trackPadding = PaddingValues(0.dp),
                                trackPaddingWithBorder = PaddingValues(0.dp),
                                thumbColorAnimationDuration = Duration.ZERO,
                                trackColorAnimationDuration = Duration.ZERO,
                                scrollbarBackgroundColorLight = Color.Transparent,
                                scrollbarBackgroundColorDark = Color.Transparent,
                                trackThicknessExpanded = 20.dp,
                                expandAnimationDuration = Duration.ZERO,
                            ),
                    )
                Box(
                    Modifier.size(CONTAINER_SIZE)
                        .background(CONTENT_COLOR)
                        .testTag(TAG)
                        .scrollIndicator(state, style = style, enabled = false)
                ) {
                    Box(Modifier.fillMaxWidth().verticalScroll(state)) { TallContent() }
                }
            }
        }
        rule.waitForIdle()
        assertTrue(scrollState.canScrollForward)

        val before = paintedTrackWidthPx()
        val width = rule.onNodeWithTag(TAG).fetchSemanticsNode().size.width
        rule.onNodeWithTag(TAG).performMouseInput {
            enter(Offset(width - 4f, 40f))
            moveTo(Offset(width - 4f, 42f))
        }
        rule.waitForIdle()

        assertEquals(before, paintedTrackWidthPx(), "A disabled indicator must not expand on hover")
    }

    @Test
    fun `a disabled indicator does not forward the reserved-lane wheel`() {
        assumeTrue("The lane is only reserved on macOS", hostOs == OS.MacOS)
        lateinit var scrollState: ScrollState

        rule.setContent {
            IntUiTheme {
                val state = rememberScrollState()
                scrollState = state
                AlwaysVisibleIndicator(state, enabled = false)
            }
        }
        rule.waitForIdle()

        rule.onNodeWithTag(TAG).performMouseInput {
            moveTo(Offset(width - 2f, height / 2f))
            scroll(3f)
        }
        rule.waitForIdle()

        assertEquals(0, scrollState.value, "A disabled indicator must not forward the lane wheel")
    }

    @Test
    fun `the track does not expand before expandDelay`() {
        rule.mainClock.autoAdvance = false
        rule.setContent {
            IntUiTheme {
                val state = rememberScrollState()
                ExpandingAlwaysVisibleIndicator(state, expandDelay = 150.milliseconds, collapseDelay = 300.milliseconds)
            }
        }
        rule.waitForIdle()
        val collapsed = paintedTrackWidthPx()
        assertTrue(collapsed > 0, "An always-visible indicator should paint at rest")

        val width = rule.onNodeWithTag(TAG).fetchSemanticsNode().size.width
        rule.onNodeWithTag(TAG).performMouseInput {
            enter(Offset(width - 4f, 40f))
            moveTo(Offset(width - 4f, 42f))
        }
        rule.mainClock.advanceTimeBy(16)
        rule.waitForIdle()
        assertEquals(collapsed, paintedTrackWidthPx(), "Hover must not expand the track in the first frame")

        rule.mainClock.advanceTimeBy(133)
        rule.waitForIdle()
        assertEquals(collapsed, paintedTrackWidthPx(), "The track must not expand before expandDelay")

        rule.mainClock.advanceTimeBy(20)
        rule.waitForIdle()
        assertTrue(paintedTrackWidthPx() > collapsed, "The track should expand once expandDelay elapses")
    }

    @Test
    fun `the track does not collapse before collapseDelay`() {
        rule.mainClock.autoAdvance = false
        rule.setContent {
            IntUiTheme {
                val state = rememberScrollState()
                ExpandingAlwaysVisibleIndicator(state, expandDelay = 0.milliseconds, collapseDelay = 300.milliseconds)
            }
        }
        rule.waitForIdle()
        val atRest = paintedTrackWidthPx()
        assertTrue(atRest > 0, "An always-visible indicator should paint at rest")

        val width = rule.onNodeWithTag(TAG).fetchSemanticsNode().size.width
        rule.onNodeWithTag(TAG).performMouseInput {
            enter(Offset(width - 4f, 40f))
            moveTo(Offset(width - 4f, 42f))
        }
        rule.mainClock.advanceTimeBy(50)
        rule.waitForIdle()
        val expanded = paintedTrackWidthPx()
        assertTrue(expanded > atRest, "Hovering the track should expand the indicator")

        rule.onNodeWithTag(TAG).performMouseInput { moveTo(Offset(10f, 40f)) }
        rule.mainClock.advanceTimeBy(280)
        rule.waitForIdle()
        assertEquals(expanded, paintedTrackWidthPx(), "The track must not collapse before collapseDelay")

        rule.mainClock.advanceTimeBy(40)
        rule.waitForIdle()
        assertTrue(paintedTrackWidthPx() < expanded, "The track should collapse once collapseDelay elapses")
    }

    @Test
    fun `a collapsed overlay track does not take clicks`() {
        var contentPresses = 0
        lateinit var scrollState: ScrollState

        rule.setContent {
            IntUiTheme {
                val state = rememberScrollState()
                scrollState = state
                val style = whenScrollingStyle(lingerDuration = 10_000.milliseconds)
                Box(
                    Modifier.size(CONTAINER_SIZE)
                        .background(CONTENT_COLOR)
                        .testTag(TAG)
                        .scrollIndicator(state, style = style)
                ) {
                    Box(Modifier.fillMaxWidth().verticalScroll(state).countPresses { contentPresses++ }) {
                        TallContent()
                    }
                }
            }
        }
        rule.waitForIdle()

        val width = rule.onNodeWithTag(TAG).fetchSemanticsNode().size.width
        rule.onNodeWithTag(TAG).performMouseInput {
            enter(Offset(width - 4f, 40f))
            moveTo(Offset(width - 4f, 42f))
        }
        rule.waitForIdle()
        assertTrue(hasIndicatorPixels(), "Hovering the track should reveal the overlay")

        // Freeze the clock so waitForIdle cannot run out the linger, then leave the track so it
        // collapses while staying visible.
        rule.mainClock.autoAdvance = false
        rule.onNodeWithTag(TAG).performMouseInput { moveTo(Offset(10f, 40f)) }
        rule.mainClock.advanceTimeBy(16)
        rule.waitForIdle()
        assertTrue(hasIndicatorPixels(), "The overlay should stay visible while the linger runs")

        val size = rule.onNodeWithTag(TAG).fetchSemanticsNode().size
        val before = scrollState.value
        rule.onNodeWithTag(TAG).performMouseInput {
            // Teleport onto the track without a move event, so hover does not expand it first.
            updatePointerTo(Offset(size.width - 4f, size.height - 6f))
            press()
            release()
        }
        rule.waitForIdle()

        assertEquals(before, scrollState.value, "A collapsed overlay track must not scroll on click")
        assertEquals(1, contentPresses, "The click should reach the content under the collapsed overlay")
    }

    @Test
    fun `windows and linux styles page on a track click`() {
        assertEquals(TrackClickBehavior.NextPage, ScrollbarStyle.windowsAndLinuxLight().trackClickBehavior)
        assertEquals(TrackClickBehavior.NextPage, ScrollbarStyle.windowsAndLinuxDark().trackClickBehavior)
    }

    @Test
    fun `WhenScrolling factories wait before expanding`() {
        assertEquals(150.milliseconds, WhenScrolling.macOs().expandDelay)
        assertEquals(300.milliseconds, WhenScrolling.macOs().collapseDelay)
        assertEquals(150.milliseconds, WhenScrolling.windowsAndLinux().expandDelay)
        assertEquals(300.milliseconds, WhenScrolling.windowsAndLinux().collapseDelay)
    }

    @Test
    fun `pressing the track pages towards the press with NextPage`() {
        lateinit var scrollState: ScrollState

        rule.setContent {
            IntUiTheme {
                val state = rememberScrollState()
                scrollState = state
                AlwaysVisibleIndicator(state, clickBehavior = TrackClickBehavior.NextPage)
            }
        }
        rule.waitForIdle()

        val size = rule.onNodeWithTag(TAG).fetchSemanticsNode().size
        rule.onNodeWithTag(TAG).performMouseInput {
            moveTo(Offset(size.width - 4f, size.height - 6f))
            press()
            release()
        }
        rule.waitForIdle()

        // A single press pages down by exactly one viewport.
        assertEquals(size.height, scrollState.value, "One press should scroll down by one page")
    }

    @Test
    fun `holding the track does not page back past the press with NextPage`() {
        lateinit var scrollState: ScrollState

        rule.setContent {
            IntUiTheme {
                val state = rememberScrollState()
                scrollState = state
                AlwaysVisibleIndicator(state, clickBehavior = TrackClickBehavior.NextPage)
            }
        }
        rule.waitForIdle()

        val size = rule.onNodeWithTag(TAG).fetchSemanticsNode().size
        // Press below the thumb (which parks at the top), so paging has somewhere to go.
        rule.onNodeWithTag(TAG).performMouseInput {
            moveTo(Offset(size.width - 4f, size.height * 0.6f))
            press()
        }
        // The first page lands the thumb over the press, so paging stops on its own: with a ScrollState a
        // page moves the thumb by exactly one thumb length. The latch in scrollTowards guards the lazy-layout
        // case where estimates shift mid-gesture; pagesBackPastPress pins it in the geometry tests.
        rule.mainClock.advanceTimeBy(2_000)
        rule.waitForIdle()
        val scrollWhileHeld = scrollState.value

        rule.onNodeWithTag(TAG).performMouseInput { release() }
        rule.waitForIdle()

        // The paging direction is latched, so it must never reverse and oscillate back up.
        assertTrue(scrollWhileHeld > 0, "Holding the track should page towards the press")
        assertTrue(
            scrollState.value >= scrollWhileHeld,
            "Paging must not reverse after reaching the press. Was $scrollWhileHeld, now ${scrollState.value}",
        )
    }

    @Test
    fun `keepVisible keeps the indicator drawn when only visible while scrolling`() {
        rule.setContent {
            IntUiTheme {
                val state = rememberScrollState()
                val style = whenScrollingStyle()
                Box(
                    Modifier.size(CONTAINER_SIZE)
                        .background(CONTENT_COLOR)
                        .testTag(TAG)
                        .scrollIndicator(state, style = style, keepVisible = true)
                ) {
                    Box(Modifier.fillMaxWidth().verticalScroll(state)) { TallContent() }
                }
            }
        }
        rule.waitForIdle()

        // Reveal it by hovering the track, then let the linger elapse: keepVisible must hold it on screen.
        val width = rule.onNodeWithTag(TAG).fetchSemanticsNode().size.width
        rule.onNodeWithTag(TAG).performMouseInput {
            enter(Offset(width - 4f, 40f))
            moveTo(Offset(width - 4f, 42f))
            exit(Offset(-10f, -10f))
        }
        rule.mainClock.advanceTimeBy(2_000)
        rule.waitForIdle()

        assertTrue(hasIndicatorPixels(), "keepVisible should keep the indicator on screen")
    }

    @Test
    fun `switching the style at runtime updates the visibility behaviour`() {
        var alwaysVisible by mutableStateOf(false)

        rule.setContent {
            IntUiTheme {
                val state = rememberScrollState()
                val style = if (alwaysVisible) alwaysVisibleStyle() else whenScrollingStyle()
                Box(
                    Modifier.size(CONTAINER_SIZE)
                        .background(CONTENT_COLOR)
                        .testTag(TAG)
                        .scrollIndicator(state, style = style)
                ) {
                    Box(Modifier.fillMaxWidth().verticalScroll(state)) { TallContent() }
                }
            }
        }
        rule.waitForIdle()
        assertFalse(hasIndicatorPixels(), "The indicator should start hidden while only visible on scroll")

        alwaysVisible = true
        rule.mainClock.advanceTimeBy(1_000)
        rule.waitForIdle()

        assertTrue(hasIndicatorPixels(), "Switching to an always-visible style should reveal the indicator")
    }

    @Test
    fun `dragging the thumb in reverse layout scrolls the content`() {
        lateinit var listState: LazyListState

        rule.setContent {
            IntUiTheme {
                val state = rememberLazyListState()
                listState = state
                val style = alwaysVisibleStyle()
                Box(
                    Modifier.size(CONTAINER_SIZE)
                        .background(CONTENT_COLOR)
                        .testTag(TAG)
                        .scrollIndicator(state, style = style, reverseLayout = true)
                ) {
                    LazyColumn(Modifier.fillMaxWidth(), state = state, reverseLayout = true) {
                        items(30) { Box(Modifier.fillMaxWidth().height(20.dp).background(CONTENT_COLOR)) }
                    }
                }
            }
        }
        rule.waitForIdle()
        assertEquals(0, listState.firstVisibleItemIndex, "The reversed list should start at the beginning")

        // The thumb is parked at the bottom, so drag it upwards to scroll.
        val size = rule.onNodeWithTag(TAG).fetchSemanticsNode().size
        rule.onNodeWithTag(TAG).performMouseInput {
            moveTo(Offset(size.width - 4f, size.height - 10f))
            press()
            moveTo(Offset(size.width - 4f, size.height / 2f))
            release()
        }
        rule.waitForIdle()

        assertTrue(listState.firstVisibleItemIndex > 0, "Dragging the thumb should scroll the reversed list")
    }

    @Test
    fun `moving the pointer over hidden content does not reveal the indicator`() {
        rule.setContent {
            IntUiTheme {
                val state = rememberScrollState()
                val style = whenScrollingStyle(lingerDuration = 500.milliseconds)
                Box(
                    Modifier.size(CONTAINER_SIZE)
                        .background(CONTENT_COLOR)
                        .testTag(TAG)
                        .scrollIndicator(state, style = style)
                ) {
                    Box(Modifier.fillMaxWidth().verticalScroll(state)) { TallContent() }
                }
            }
        }
        rule.waitForIdle()
        assertFalse(hasIndicatorPixels(), "The indicator should start hidden")

        // Move over the content, away from the track. Like the scrollable containers, the keep-visible
        // latch only prolongs an already-visible indicator; it must not reveal a hidden one.
        rule.onNodeWithTag(TAG).performMouseInput {
            enter(Offset(10f, 40f))
            moveTo(Offset(12f, 42f))
        }
        rule.waitForIdle()

        assertFalse(hasIndicatorPixels(), "Moving over hidden content must not reveal the indicator")
    }

    @Test
    fun `moving the pointer keeps a visible indicator on screen`() {
        rule.setContent {
            IntUiTheme {
                val state = rememberScrollState()
                val style = whenScrollingStyle(lingerDuration = 500.milliseconds)
                Box(
                    Modifier.size(CONTAINER_SIZE)
                        .background(CONTENT_COLOR)
                        .testTag(TAG)
                        .scrollIndicator(state, style = style)
                ) {
                    Box(Modifier.fillMaxWidth().verticalScroll(state)) { TallContent() }
                }
            }
        }
        rule.waitForIdle()

        // Hover the track to reveal it, then keep the pointer moving over the content, away from the
        // track, for longer than the linger duration.
        val width = rule.onNodeWithTag(TAG).fetchSemanticsNode().size.width
        rule.onNodeWithTag(TAG).performMouseInput {
            enter(Offset(width - 4f, 40f))
            moveTo(Offset(width - 4f, 42f))
        }
        rule.waitForIdle()
        assertTrue(hasIndicatorPixels(), "Hovering the track should reveal the indicator")

        repeat(6) { step ->
            rule.onNodeWithTag(TAG).performMouseInput { moveTo(Offset(10f, 40f + step)) }
            rule.mainClock.advanceTimeBy(200)
        }
        rule.waitForIdle()

        assertTrue(hasIndicatorPixels(), "Continuing to move the pointer should keep the indicator visible")
    }

    @Test
    fun `the indicator hides again once the pointer stops moving`() {
        rule.setContent {
            IntUiTheme {
                val state = rememberScrollState()
                val style = whenScrollingStyle(lingerDuration = 500.milliseconds)
                Box(
                    Modifier.size(CONTAINER_SIZE)
                        .background(CONTENT_COLOR)
                        .testTag(TAG)
                        .scrollIndicator(state, style = style)
                ) {
                    Box(Modifier.fillMaxWidth().verticalScroll(state)) { TallContent() }
                }
            }
        }
        rule.waitForIdle()

        val width = rule.onNodeWithTag(TAG).fetchSemanticsNode().size.width
        rule.onNodeWithTag(TAG).performMouseInput {
            enter(Offset(width - 4f, 40f))
            moveTo(Offset(width - 4f, 42f))
        }
        rule.waitForIdle()
        assertTrue(hasIndicatorPixels(), "Hovering the track should reveal the indicator")

        // Park the pointer away from the track so hover no longer holds it open.
        rule.onNodeWithTag(TAG).performMouseInput { exit(Offset(-10f, -10f)) }
        rule.waitForIdle()

        // Let the linger elapse with the pointer still: it must fade back out.
        rule.mainClock.advanceTimeBy(2_000)
        rule.waitForIdle()

        assertFalse(hasIndicatorPixels(), "The indicator should hide once the pointer has been still")
    }

    @Test
    fun `the indicator is drawn at the start edge in RTL`() {
        rule.setContent {
            IntUiTheme {
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                    val state = rememberScrollState()
                    val style = alwaysVisibleStyle()
                    Box(
                        Modifier.size(CONTAINER_SIZE)
                            .background(CONTENT_COLOR)
                            .testTag(TAG)
                            .scrollIndicator(state, style = style)
                    ) {
                        Box(Modifier.fillMaxWidth().verticalScroll(state)) { TallContent() }
                    }
                }
            }
        }
        rule.waitForIdle()

        val image = rule.onNodeWithTag(TAG).captureToImage().toPixelMap()
        val hasLeftPixels = (0 until image.height).any { y -> image[4, y] != CONTENT_COLOR }
        val hasRightPixels = (0 until image.height).any { y -> image[image.width - 4, y] != CONTENT_COLOR }

        assertTrue(hasLeftPixels, "In RTL the indicator should be drawn on the left edge")
        assertFalse(hasRightPixels, "In RTL the indicator should not be drawn on the right edge")
    }

    @Test
    fun `dragging the thumb in RTL scrolls the content`() {
        lateinit var scrollState: ScrollState

        rule.setContent {
            IntUiTheme {
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                    val state = rememberScrollState()
                    scrollState = state
                    val style = alwaysVisibleStyle()
                    Box(
                        Modifier.size(CONTAINER_SIZE)
                            .background(CONTENT_COLOR)
                            .testTag(TAG)
                            .scrollIndicator(state, style = style)
                    ) {
                        Box(Modifier.fillMaxWidth().verticalScroll(state)) { TallContent() }
                    }
                }
            }
        }
        rule.waitForIdle()

        // The track is on the left in RTL, so hit-testing must follow it there.
        rule.onNodeWithTag(TAG).performMouseInput {
            moveTo(Offset(4f, 8f))
            press()
            moveTo(Offset(4f, 60f))
            release()
        }
        rule.waitForIdle()

        assertTrue(scrollState.value > 0, "Dragging the RTL thumb should scroll the content")
    }

    @Test
    fun `an always-visible style shows the indicator without any interaction`() {
        rule.setContent {
            IntUiTheme {
                val state = rememberScrollState()
                val style = alwaysVisibleStyle()
                Box(
                    Modifier.size(CONTAINER_SIZE)
                        .background(CONTENT_COLOR)
                        .testTag(TAG)
                        .scrollIndicator(state, style = style)
                ) {
                    Box(Modifier.fillMaxWidth().verticalScroll(state)) { TallContent() }
                }
            }
        }
        rule.waitForIdle()

        // No pointer input at all, and well past any linger: it must still be visible.
        rule.mainClock.advanceTimeBy(5_000)
        rule.waitForIdle()

        assertTrue(hasIndicatorPixels(), "An always-visible indicator should never hide")
    }

    @Test
    fun `a secondary-button press on the track does not scroll`() {
        lateinit var scrollState: ScrollState

        rule.setContent {
            IntUiTheme {
                val state = rememberScrollState()
                scrollState = state
                AlwaysVisibleIndicator(state)
            }
        }
        rule.waitForIdle()

        val size = rule.onNodeWithTag(TAG).fetchSemanticsNode().size
        rule.onNodeWithTag(TAG).performMouseInput {
            moveTo(Offset(size.width - 4f, size.height - 6f))
            press(MouseButton.Secondary)
            release(MouseButton.Secondary)
        }
        rule.waitForIdle()

        assertEquals(0, scrollState.value, "Only the primary button should scroll, as in the Swing scrollbar")
    }

    @Test
    fun `dragging past the end and back does not move the content early`() {
        lateinit var scrollState: ScrollState

        rule.setContent {
            IntUiTheme {
                val state = rememberScrollState()
                scrollState = state
                AlwaysVisibleIndicator(state)
            }
        }
        rule.waitForIdle()

        val size = rule.onNodeWithTag(TAG).fetchSemanticsNode().size
        val trackX = size.width - 4f
        rule.onNodeWithTag(TAG).performMouseInput {
            moveTo(Offset(trackX, 8f))
            press()
            // Drag far past the bottom end, so a lot of travel goes unconsumed.
            moveTo(Offset(trackX, size.height * 4f))
        }
        rule.waitForIdle()
        val atEnd = scrollState.value
        assertEquals(scrollState.maxValue, atEnd, "Dragging past the end should pin the content at the end")

        // Come back a little. The overshoot must be absorbed first, so this must not move yet.
        rule.onNodeWithTag(TAG).performMouseInput { moveTo(Offset(trackX, size.height * 3f)) }
        rule.waitForIdle()
        assertEquals(atEnd, scrollState.value, "Reversing within the overshoot must not scroll back yet")

        rule.onNodeWithTag(TAG).performMouseInput { release() }
        rule.waitForIdle()
    }

    @Test
    fun `draws a horizontal indicator along the bottom edge`() {
        rule.setContent {
            IntUiTheme {
                val state = rememberScrollState()
                val style = alwaysVisibleStyle()
                Box(
                    Modifier.size(CONTAINER_SIZE)
                        .background(CONTENT_COLOR)
                        .testTag(TAG)
                        .scrollIndicator(state, Orientation.Horizontal, style = style)
                ) {
                    Row(Modifier.fillMaxHeight().horizontalScroll(state)) { WideContent() }
                }
            }
        }
        rule.waitForIdle()

        val image = rule.onNodeWithTag(TAG).captureToImage().toPixelMap()
        val hasBottomPixels = (0 until image.width).any { x -> image[x, image.height - 4] != CONTENT_COLOR }
        val hasRightPixels = (0 until image.height - 20).any { y -> image[image.width - 4, y] != CONTENT_COLOR }

        assertTrue(hasBottomPixels, "A horizontal indicator should be drawn along the bottom edge")
        assertFalse(hasRightPixels, "A horizontal indicator should not be drawn along the right edge")
    }

    @Test
    fun `dragging a horizontal thumb scrolls the content`() {
        lateinit var scrollState: ScrollState

        rule.setContent {
            IntUiTheme {
                val state = rememberScrollState()
                scrollState = state
                val style = alwaysVisibleStyle()
                Box(
                    Modifier.size(CONTAINER_SIZE)
                        .background(CONTENT_COLOR)
                        .testTag(TAG)
                        .scrollIndicator(state, Orientation.Horizontal, style = style)
                ) {
                    Row(Modifier.fillMaxHeight().horizontalScroll(state)) { WideContent() }
                }
            }
        }
        rule.waitForIdle()
        assertEquals(0, scrollState.value, "The content should start unscrolled")

        val size = rule.onNodeWithTag(TAG).fetchSemanticsNode().size
        rule.onNodeWithTag(TAG).performMouseInput {
            val trackY = size.height - 4f
            moveTo(Offset(8f, trackY))
            press()
            moveTo(Offset(60f, trackY))
            release()
        }
        rule.waitForIdle()

        assertTrue(scrollState.value > 0, "Dragging the horizontal thumb should scroll the content right")
    }

    @Test
    fun `clicking a horizontal track scrolls towards the press`() {
        lateinit var scrollState: ScrollState

        rule.setContent {
            IntUiTheme {
                val state = rememberScrollState()
                scrollState = state
                val style = alwaysVisibleStyle()
                Box(
                    Modifier.size(CONTAINER_SIZE)
                        .background(CONTENT_COLOR)
                        .testTag(TAG)
                        .scrollIndicator(state, Orientation.Horizontal, style = style)
                ) {
                    Row(Modifier.fillMaxHeight().horizontalScroll(state)) { WideContent() }
                }
            }
        }
        rule.waitForIdle()

        val size = rule.onNodeWithTag(TAG).fetchSemanticsNode().size
        rule.onNodeWithTag(TAG).performMouseInput {
            moveTo(Offset(size.width - 6f, size.height - 4f))
            press()
            release()
        }
        rule.mainClock.advanceTimeBy(1_000)
        rule.waitForIdle()

        assertTrue(scrollState.value > 0, "Clicking near the horizontal track end should scroll towards it")
    }

    @Test
    fun `dragging past the end and back keeps the thumb pinned to the pointer`() {
        lateinit var scrollState: ScrollState

        rule.setContent {
            IntUiTheme {
                val state = rememberScrollState()
                scrollState = state
                AlwaysVisibleIndicator(state)
            }
        }
        rule.waitForIdle()

        val size = rule.onNodeWithTag(TAG).fetchSemanticsNode().size
        val trackX = size.width - 4f
        rule.onNodeWithTag(TAG).performMouseInput {
            moveTo(Offset(trackX, 8f))
            press()
            moveTo(Offset(trackX, size.height * 4f))
        }
        rule.waitForIdle()
        assertEquals(scrollState.maxValue, scrollState.value, "Dragging past the end should pin at the end")

        // Absolute positioning means coming back to the middle tracks the pointer immediately,
        // rather than absorbing the overshoot first.
        rule.onNodeWithTag(TAG).performMouseInput { moveTo(Offset(trackX, size.height / 2f)) }
        rule.waitForIdle()
        val middle = scrollState.value
        assertTrue(middle < scrollState.maxValue, "Returning to the middle should scroll back")
        assertTrue(middle > 0, "Returning to the middle should not jump to the start")

        rule.onNodeWithTag(TAG).performMouseInput { release() }
        rule.waitForIdle()
    }

    @Composable
    private fun WideContent() {
        repeat(30) { Box(Modifier.fillMaxHeight().width(20.dp).background(CONTENT_COLOR)) }
    }

    @Test
    fun `an always-visible indicator narrows the content on macOS`() {
        // The reservation follows the host OS, exactly as VerticallyScrollableContainer's does.
        val expectedReserved = if (hostOs == OS.MacOS) TRACK_THICKNESS else 0.dp

        rule.setContent {
            IntUiTheme {
                val state = rememberScrollState()
                val style = alwaysVisibleStyle()
                Box(Modifier.size(CONTAINER_SIZE).testTag(TAG).scrollIndicator(state, style = style)) {
                    Box(Modifier.fillMaxWidth().testTag(CONTENT_TAG).verticalScroll(state)) { TallContent() }
                }
            }
        }
        rule.waitForIdle()

        val container = rule.onNodeWithTag(TAG).fetchSemanticsNode().size
        val content = rule.onNodeWithTag(CONTENT_TAG).fetchSemanticsNode().size
        val reservedPx = with(rule.density) { expectedReserved.roundToPx() }

        assertEquals(
            container.width - reservedPx,
            content.width,
            "An always-visible indicator should reserve its own lane beside the content on macOS, and nothing elsewhere",
        )
    }

    @Test
    fun `an indicator that only shows while scrolling never narrows the content`() {
        rule.setContent {
            IntUiTheme {
                val state = rememberScrollState()
                val style = whenScrollingStyle()
                Box(Modifier.size(CONTAINER_SIZE).testTag(TAG).scrollIndicator(state, style = style)) {
                    Box(Modifier.fillMaxWidth().testTag(CONTENT_TAG).verticalScroll(state)) { TallContent() }
                }
            }
        }
        rule.waitForIdle()

        val container = rule.onNodeWithTag(TAG).fetchSemanticsNode().size
        val content = rule.onNodeWithTag(CONTENT_TAG).fetchSemanticsNode().size

        assertEquals(container.width, content.width, "A floating indicator must overlay the content, not displace it")
    }

    @Test
    fun `the wheel scrolls the content when the pointer is over the reserved lane`() {
        assumeTrue("The lane is only reserved on macOS", hostOs == OS.MacOS)
        lateinit var scrollState: ScrollState

        rule.setContent {
            IntUiTheme {
                val state = rememberScrollState()
                scrollState = state
                AlwaysVisibleIndicator(state)
            }
        }
        rule.waitForIdle()
        assertEquals(0, scrollState.value, "The content should start unscrolled")

        // The reserved lane sits outside the scrollable child, so a wheel event there must be forwarded.
        rule.onNodeWithTag(TAG).performMouseInput {
            moveTo(Offset(width - 2f, height / 2f))
            scroll(3f)
        }
        rule.waitForIdle()

        assertTrue(scrollState.value > 0, "The wheel over the reserved lane should scroll the content")
    }

    @Test
    fun `paging stops once the content reaches the scroll limit`() {
        lateinit var scrollState: ScrollState

        rule.setContent {
            IntUiTheme {
                val state = rememberScrollState()
                scrollState = state
                AlwaysVisibleIndicator(state, clickBehavior = TrackClickBehavior.NextPage)
            }
        }
        rule.waitForIdle()

        // Hold near the track end: paging must stop at the limit rather than looping forever.
        rule.onNodeWithTag(TAG).performMouseInput {
            moveTo(Offset(width - 6f, height - 2f))
            press()
        }
        rule.mainClock.advanceTimeBy(2_000)
        rule.waitForIdle()
        val atLimit = scrollState.value
        assertEquals(scrollState.maxValue, atLimit, "Paging should reach the end of the content")

        rule.mainClock.advanceTimeBy(2_000)
        rule.waitForIdle()
        rule.onNodeWithTag(TAG).performMouseInput { release() }

        assertEquals(atLimit, scrollState.value, "Paging should stop once the limit is reached")
    }

    @Test
    fun `the wheel scrolls the content when the indicator overlays it`() {
        lateinit var scrollState: ScrollState

        rule.setContent {
            IntUiTheme {
                val state = rememberScrollState()
                scrollState = state
                val style = whenScrollingStyle()
                Box(
                    Modifier.size(CONTAINER_SIZE)
                        .background(CONTENT_COLOR)
                        .testTag(TAG)
                        .scrollIndicator(state, style = style)
                ) {
                    Box(Modifier.fillMaxWidth().verticalScroll(state)) { TallContent() }
                }
            }
        }
        rule.waitForIdle()

        // No lane is reserved here, so the content sits under the track and the wheel reaches it directly.
        rule.onNodeWithTag(TAG).performMouseInput {
            moveTo(Offset(width - 2f, height / 2f))
            scroll(3f)
        }
        rule.waitForIdle()

        assertTrue(scrollState.value > 0, "The wheel over the track should scroll the overlaid content")
    }

    @Test
    fun `a vertical and a horizontal indicator can decorate the same content`() {
        lateinit var verticalState: ScrollState
        lateinit var horizontalState: ScrollState

        rule.setContent {
            IntUiTheme {
                val vertical = rememberScrollState()
                val horizontal = rememberScrollState()
                verticalState = vertical
                horizontalState = horizontal
                val style = alwaysVisibleStyle()
                Box(
                    Modifier.size(CONTAINER_SIZE)
                        .background(CONTENT_COLOR)
                        .testTag(TAG)
                        .scrollIndicator(vertical, Orientation.Vertical, style = style)
                        .scrollIndicator(horizontal, Orientation.Horizontal, style = style)
                ) {
                    Box(Modifier.horizontalScroll(horizontal).verticalScroll(vertical)) {
                        Box(Modifier.size(600.dp).background(CONTENT_COLOR))
                    }
                }
            }
        }
        rule.waitForIdle()

        // Chaining two modifiers is not the intended public shape for 2D scrolling. When CMP 1.13
        // makes scrollable2D work on desktop, an overload will take a Scrollable2DState and draw
        // both axes. This pins the axis-neutral behaviour that overload will build on.
        rule.runOnIdle { runBlocking { verticalState.scrollTo(120) } }
        rule.runOnIdle { runBlocking { horizontalState.scrollTo(80) } }
        rule.waitForIdle()

        assertEquals(120, verticalState.value, "The vertical axis should scroll independently")
        assertEquals(80, horizontalState.value, "The horizontal axis should scroll independently")
    }

    @Test
    fun `pointer movement prolongs the linger without keepVisible`() {
        rule.setContent {
            IntUiTheme {
                val state = rememberScrollState()
                val style = whenScrollingStyle(lingerDuration = 500.milliseconds)
                Box(
                    Modifier.size(CONTAINER_SIZE)
                        .background(CONTENT_COLOR)
                        .testTag(TAG)
                        .scrollIndicator(state, style = style)
                ) {
                    Box(Modifier.fillMaxWidth().verticalScroll(state)) { TallContent() }
                }
            }
        }
        rule.waitForIdle()

        val width = rule.onNodeWithTag(TAG).fetchSemanticsNode().size.width
        rule.onNodeWithTag(TAG).performMouseInput { enter(Offset(width - 4f, 40f)) }
        rule.waitForIdle()
        assertTrue(hasIndicatorPixels(), "Hovering the track should reveal the indicator")

        // Keep nudging the pointer: each move restarts the linger, so it must stay drawn well past
        // a single lingerDuration. This is the default, with no keepVisible opt-in.
        repeat(6) { step ->
            rule.onNodeWithTag(TAG).performMouseInput { moveTo(Offset(width / 2f, 40f + step)) }
            rule.mainClock.advanceTimeBy(300)
            rule.waitForIdle()
        }
        assertTrue(hasIndicatorPixels(), "Movement over the content should prolong the linger")

        // Once the pointer goes still for the whole linger, it finally hides.
        rule.onNodeWithTag(TAG).performMouseInput { exit(Offset(-10f, -10f)) }
        rule.mainClock.advanceTimeBy(2_000)
        rule.waitForIdle()
        assertFalse(hasIndicatorPixels(), "A still pointer should let the indicator hide")
    }

    @Test
    fun `track paging repeats at the Swing cadence`() {
        lateinit var scrollState: ScrollState

        rule.mainClock.autoAdvance = false
        rule.setContent {
            IntUiTheme {
                val state = rememberScrollState()
                scrollState = state
                AlwaysVisibleIndicator(state, clickBehavior = TrackClickBehavior.NextPage)
            }
        }
        rule.waitForIdle()

        // Press just below the thumb so paging runs downwards for the whole scroll range.
        rule.onNodeWithTag(TAG).performMouseInput {
            moveTo(Offset(width - 6f, height - 8f))
            press()
        }

        // First page lands immediately, before the initial delay elapses.
        rule.mainClock.advanceTimeBy(16)
        rule.waitForIdle()
        val afterFirstPage = scrollState.value
        assertTrue(afterFirstPage > 0, "The press should page once straight away")

        // The 2nd page waits DELAY_BEFORE_SECOND_SCROLL_ON_TRACK_PRESS (300 ms), so nothing moves at 250 ms.
        rule.mainClock.advanceTimeBy(250)
        rule.waitForIdle()
        assertEquals(afterFirstPage, scrollState.value, "The 2nd page must wait for the initial 300 ms delay")

        // Crossing 300 ms releases the 2nd page.
        rule.mainClock.advanceTimeBy(100)
        rule.waitForIdle()
        val afterSecondPage = scrollState.value
        assertTrue(afterSecondPage > afterFirstPage, "The 2nd page should land once the initial delay elapses")

        rule.onNodeWithTag(TAG).performMouseInput { release() }
    }

    @Test
    fun `track paging repeats every 60ms after the initial delay`() {
        lateinit var scrollState: ScrollState

        rule.mainClock.autoAdvance = false
        rule.setContent {
            IntUiTheme {
                val state = rememberScrollState()
                scrollState = state
                val style = alwaysVisibleStyle(TrackClickBehavior.NextPage)
                Box(
                    Modifier.size(CONTAINER_SIZE)
                        .background(CONTENT_COLOR)
                        .testTag(TAG)
                        .scrollIndicator(state, style = style)
                ) {
                    // 20 viewports of content, so paging has room for many repeats before the limit.
                    Box(Modifier.fillMaxWidth().verticalScroll(state)) {
                        Column { repeat(200) { Box(Modifier.fillMaxWidth().height(20.dp).background(CONTENT_COLOR)) } }
                    }
                }
            }
        }
        rule.waitForIdle()

        rule.onNodeWithTag(TAG).performMouseInput {
            moveTo(Offset(width - 6f, height - 8f))
            press()
        }
        rule.mainClock.advanceTimeBy(16)
        rule.waitForIdle()
        val afterFirstPage = scrollState.value

        // Cross the 300 ms initial delay, then count how many pages land over the next 300 ms.
        // At the Swing cadence of 60 ms that is 5 more pages; at the old 100 ms it would only be 3.
        rule.mainClock.advanceTimeBy(300)
        rule.waitForIdle()
        val afterSecondPage = scrollState.value
        val pageSize = afterSecondPage - afterFirstPage
        assertTrue(pageSize > 0, "The 2nd page should land once the initial delay elapses")

        rule.mainClock.advanceTimeBy(300)
        rule.waitForIdle()
        val pagesInWindow = (scrollState.value - afterSecondPage) / pageSize

        assertEquals(5, pagesInWindow, "300 ms of repeats at 60 ms each should page 5 more times")

        rule.onNodeWithTag(TAG).performMouseInput { release() }
    }

    @Test
    fun `a horizontal indicator in RTL parks the thumb at the visual start`() {
        lateinit var scrollState: ScrollState

        rule.setContent {
            IntUiTheme {
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                    val state = rememberScrollState()
                    scrollState = state
                    val style = alwaysVisibleStyle()
                    Box(
                        Modifier.size(CONTAINER_SIZE)
                            .background(CONTENT_COLOR)
                            .testTag(TAG)
                            .scrollIndicator(state, Orientation.Horizontal, style = style)
                    ) {
                        Box(Modifier.horizontalScroll(state)) {
                            Box(Modifier.size(width = 600.dp, height = 40.dp).background(CONTENT_COLOR))
                        }
                    }
                }
            }
        }
        rule.waitForIdle()
        assertEquals(0, scrollState.value, "The content should start unscrolled")

        // scrollOffset is measured from the visual start, which in RTL is the RIGHT edge, so an
        // unscrolled horizontal indicator must park its thumb at the right of the track.
        val image = rule.onNodeWithTag(TAG).captureToImage().toPixelMap()
        val trackY = image.height - 4
        val leftHalf = (0 until image.width / 2).count { x -> image[x, trackY] != CONTENT_COLOR }
        val rightHalf = (image.width / 2 until image.width).count { x -> image[x, trackY] != CONTENT_COLOR }

        assertTrue(
            rightHalf > leftHalf,
            "In RTL the unscrolled thumb should sit in the right half of the track, " +
                "but found $leftHalf px left vs $rightHalf px right",
        )
    }

    @Test
    fun `dragging a horizontal thumb in RTL scrolls towards the pointer`() {
        lateinit var scrollState: ScrollState

        rule.setContent {
            IntUiTheme {
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                    val state = rememberScrollState()
                    scrollState = state
                    val style = alwaysVisibleStyle()
                    Box(
                        Modifier.size(CONTAINER_SIZE)
                            .background(CONTENT_COLOR)
                            .testTag(TAG)
                            .scrollIndicator(state, Orientation.Horizontal, style = style)
                    ) {
                        Box(Modifier.horizontalScroll(state)) {
                            Box(Modifier.size(width = 600.dp, height = 40.dp).background(CONTENT_COLOR))
                        }
                    }
                }
            }
        }
        rule.waitForIdle()

        // The thumb parks at the right in RTL, so dragging it leftwards must scroll the content.
        val size = rule.onNodeWithTag(TAG).fetchSemanticsNode().size
        rule.onNodeWithTag(TAG).performMouseInput {
            moveTo(Offset(size.width - 6f, size.height - 4f))
            press()
            moveTo(Offset(size.width / 2f, size.height - 4f))
        }
        rule.waitForIdle()
        val scrolled = scrollState.value
        rule.onNodeWithTag(TAG).performMouseInput { release() }

        assertTrue(scrolled > 0, "Dragging the RTL thumb inwards should scroll the content, but value was $scrolled")
    }

    @Test
    fun `dragging the thumb keeps the grabbed point under the pointer`() {
        lateinit var scrollState: ScrollState

        rule.setContent {
            IntUiTheme {
                val state = rememberScrollState()
                scrollState = state
                AlwaysVisibleIndicator(state)
            }
        }
        rule.waitForIdle()

        // Park the thumb mid-track and grab it near its top edge.
        rule.runOnIdle { runBlocking { scrollState.scrollTo(200) } }
        rule.waitForIdle()

        val size = rule.onNodeWithTag(TAG).fetchSemanticsNode().size
        val thumb = thumbBounds() ?: error("The thumb should be drawn")
        val grabY = thumb.first + 2f

        // Nudge the pointer by one pixel. Swing keeps the grabbed point under the cursor
        // (DefaultScrollBarUI.setValueFrom clamps `y - myOffset`), so a 1px move scrolls about one
        // track pixel worth of content. Snapping the thumb centre to the pointer instead would jump
        // by half the thumb height at once.
        rule.onNodeWithTag(TAG).performMouseInput {
            moveTo(Offset(size.width - 4f, grabY))
            press()
            moveTo(Offset(size.width - 4f, grabY + 1f))
        }
        rule.waitForIdle()
        val afterNudge = scrollState.value
        rule.onNodeWithTag(TAG).performMouseInput { release() }

        val thumbHeight = thumb.second - thumb.first
        val jumped = kotlin.math.abs(afterNudge - 200)
        val maxReasonable = (thumbHeight * scrollState.maxValue) / size.height / 4
        assertTrue(
            jumped <= maxReasonable.coerceAtLeast(20),
            "A 1px drag should scroll a little, but the content moved $jumped px (thumb is ${thumbHeight}px tall)",
        )
    }

    /** The first and last y of the thumb in the track column, or null when nothing is drawn. */
    private fun thumbBounds(): Pair<Int, Int>? {
        val image = rule.onNodeWithTag(TAG).captureToImage().toPixelMap()
        val trackX = image.width - 4
        val ys = (0 until image.height).filter { y -> image[trackX, y] != CONTENT_COLOR }
        return if (ys.isEmpty()) null else ys.first() to ys.last()
    }

    @Test
    fun `paging resumes when the pointer moves past the thumb again`() {
        lateinit var scrollState: ScrollState

        rule.mainClock.autoAdvance = false
        rule.setContent {
            IntUiTheme {
                val state = rememberScrollState()
                scrollState = state
                val style = alwaysVisibleStyle(TrackClickBehavior.NextPage)
                Box(
                    Modifier.size(CONTAINER_SIZE)
                        .background(CONTENT_COLOR)
                        .testTag(TAG)
                        .scrollIndicator(state, style = style)
                ) {
                    Box(Modifier.fillMaxWidth().verticalScroll(state)) {
                        Column { repeat(200) { Box(Modifier.fillMaxWidth().height(20.dp).background(CONTENT_COLOR)) } }
                    }
                }
            }
        }
        rule.waitForIdle()

        val size = rule.onNodeWithTag(TAG).fetchSemanticsNode().size

        // Press just below the thumb and let paging run until the thumb catches up with the pointer.
        rule.onNodeWithTag(TAG).performMouseInput {
            moveTo(Offset(size.width - 6f, size.height * 0.3f))
            press()
        }
        rule.mainClock.advanceTimeBy(2_000)
        rule.waitForIdle()
        val settled = scrollState.value
        assertTrue(settled > 0, "Paging should scroll while the pointer is past the thumb")

        // Now drag further down the track, still holding. Swing restarts its repeat timer on every
        // drag (DefaultScrollBarUI.mouseDragged -> startScrollTimerIfNecessary), so paging must resume.
        rule.onNodeWithTag(TAG).performMouseInput { moveTo(Offset(size.width - 6f, size.height - 4f)) }
        rule.mainClock.advanceTimeBy(2_000)
        rule.waitForIdle()
        val afterMove = scrollState.value
        rule.onNodeWithTag(TAG).performMouseInput { release() }

        assertTrue(
            afterMove > settled,
            "Moving the pointer further down the track should resume paging, but it stayed at $settled",
        )
    }

    @Test
    fun `the lane wheel matches the content wheel direction for a horizontal RTL indicator`() {
        assumeTrue("The lane is only reserved on macOS", hostOs == OS.MacOS)
        lateinit var scrollState: ScrollState

        rule.setContent {
            IntUiTheme {
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                    val state = rememberScrollState()
                    scrollState = state
                    AlwaysVisibleIndicator(state)
                }
            }
        }
        rule.waitForIdle()

        val size = rule.onNodeWithTag(TAG).fetchSemanticsNode().size

        // Wheel over the content and record the direction it moves.
        rule.runOnIdle { runBlocking { scrollState.scrollTo(0) } }
        rule.onNodeWithTag(TAG).performMouseInput {
            moveTo(Offset(size.width / 2f, size.height / 2f))
            scroll(3f)
        }
        rule.waitForIdle()
        val contentDelta = scrollState.value
        assertTrue(contentDelta != 0, "Wheeling over the content should scroll it")

        // Wheel the same way over the reserved lane at the bottom edge.
        rule.runOnIdle { runBlocking { scrollState.scrollTo(0) } }
        rule.onNodeWithTag(TAG).performMouseInput {
            moveTo(Offset(size.width / 2f, size.height - 2f))
            scroll(3f)
        }
        rule.waitForIdle()
        val laneDelta = scrollState.value

        assertEquals(
            contentDelta > 0,
            laneDelta > 0,
            "The lane wheel must scroll the same direction as the content wheel (content=$contentDelta, lane=$laneDelta)",
        )
    }

    @Composable
    private fun AlwaysVisibleIndicator(
        state: ScrollState,
        clickBehavior: TrackClickBehavior = TrackClickBehavior.JumpToSpot,
        enabled: Boolean = true,
    ) {
        val style = alwaysVisibleStyle(clickBehavior)
        Box(
            Modifier.size(CONTAINER_SIZE)
                .background(CONTENT_COLOR)
                .testTag(TAG)
                .scrollIndicator(state, style = style, enabled = enabled)
        ) {
            Box(Modifier.fillMaxWidth().verticalScroll(state)) { TallContent() }
        }
    }

    @Composable
    private fun ExpandingAlwaysVisibleIndicator(state: ScrollState, expandDelay: Duration, collapseDelay: Duration) {
        val base = JewelTheme.scrollbarStyle
        val style =
            remember(base, expandDelay, collapseDelay) {
                ScrollbarStyle(
                    colors = base.colors,
                    metrics = base.metrics,
                    trackClickBehavior = TrackClickBehavior.JumpToSpot,
                    scrollbarVisibility =
                        ScrollbarVisibility.AlwaysVisible(
                            trackThickness = 8.dp,
                            trackPadding = PaddingValues(0.dp),
                            trackPaddingWithBorder = PaddingValues(0.dp),
                            thumbColorAnimationDuration = Duration.ZERO,
                            trackColorAnimationDuration = Duration.ZERO,
                            scrollbarBackgroundColorLight = Color.Transparent,
                            scrollbarBackgroundColorDark = Color.Transparent,
                            trackThicknessExpanded = 20.dp,
                            // A zero-duration tween drops the delay, so keep a one-frame animation.
                            expandAnimationDuration = 16.milliseconds,
                            expandDelay = expandDelay,
                            collapseDelay = collapseDelay,
                        ),
                )
            }
        Box(
            Modifier.size(CONTAINER_SIZE).background(CONTENT_COLOR).testTag(TAG).scrollIndicator(state, style = style)
        ) {
            Box(Modifier.fillMaxWidth().verticalScroll(state)) { TallContent() }
        }
    }

    @Composable
    private fun TallContent() {
        Column { repeat(30) { Box(Modifier.fillMaxWidth().height(20.dp).background(CONTENT_COLOR)) } }
    }

    /** `true` when any pixel in the track column differs from the flat content colour. */
    private fun hasIndicatorPixels(): Boolean {
        val image = rule.onNodeWithTag(TAG).captureToImage().toPixelMap()
        val trackX = image.width - 4
        return (0 until image.height).any { y -> image[trackX, y] != CONTENT_COLOR }
    }

    /** How many columns from the trailing edge contain any non-content pixel. */
    private fun paintedTrackWidthPx(): Int {
        val image = rule.onNodeWithTag(TAG).captureToImage().toPixelMap()
        var width = 0
        for (x in image.width - 1 downTo 0) {
            val painted = (0 until image.height).any { y -> image[x, y] != CONTENT_COLOR }
            if (!painted) break
            width++
        }
        return width
    }

    @Composable
    private fun alwaysVisibleStyle(clickBehavior: TrackClickBehavior = TrackClickBehavior.JumpToSpot): ScrollbarStyle {
        val base = JewelTheme.scrollbarStyle
        return remember(base, clickBehavior) {
            ScrollbarStyle(
                colors = base.colors,
                metrics = base.metrics,
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
        }
    }

    @Composable
    private fun whenScrollingStyle(
        lingerDuration: Duration = Duration.ZERO,
        expandDelay: Duration = Duration.ZERO,
        collapseDelay: Duration = Duration.ZERO,
        trackThickness: Dp = 8.dp,
        trackThicknessExpanded: Dp = 14.dp,
    ): ScrollbarStyle {
        val base = JewelTheme.scrollbarStyle
        return remember(base, lingerDuration, expandDelay, collapseDelay, trackThickness, trackThicknessExpanded) {
            ScrollbarStyle(
                colors = base.colors,
                metrics = base.metrics,
                trackClickBehavior = TrackClickBehavior.JumpToSpot,
                scrollbarVisibility =
                    ScrollbarVisibility.WhenScrolling(
                        trackThickness = trackThickness,
                        trackThicknessExpanded = trackThicknessExpanded,
                        trackPadding = PaddingValues(2.dp),
                        trackPaddingWithBorder = PaddingValues(2.dp),
                        trackColorAnimationDuration = Duration.ZERO,
                        expandAnimationDuration = Duration.ZERO,
                        thumbColorAnimationDuration = Duration.ZERO,
                        lingerDuration = lingerDuration,
                        expandDelay = expandDelay,
                        collapseDelay = collapseDelay,
                    ),
            )
        }
    }

    private companion object {
        const val TAG = "indicator-container"
        const val CONTENT_TAG = "indicator-content"
        val CONTAINER_SIZE = 200.dp
        val TRACK_THICKNESS = 14.dp
        val CONTENT_COLOR = Color.Red
    }
}

private fun Modifier.countPresses(onPress: () -> Unit): Modifier =
    pointerInput(Unit) {
        awaitEachGesture {
            awaitFirstDown(requireUnconsumed = true)
            onPress()
        }
    }
