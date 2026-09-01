// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jewel.ui.component

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.MutatePriority
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.staggeredgrid.LazyStaggeredGridState
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.rememberLazyStaggeredGridState
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.MouseButton
import androidx.compose.ui.test.ScrollWheel
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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.intui.standalone.styling.macOs
import org.jetbrains.jewel.intui.standalone.styling.tabStripWindowsAndLinuxDark
import org.jetbrains.jewel.intui.standalone.styling.tabStripWindowsAndLinuxLight
import org.jetbrains.jewel.intui.standalone.styling.windowsAndLinux
import org.jetbrains.jewel.intui.standalone.styling.windowsAndLinuxDark
import org.jetbrains.jewel.intui.standalone.styling.windowsAndLinuxLight
import org.jetbrains.jewel.intui.standalone.theme.IntUiTheme
import org.jetbrains.jewel.ui.component.styling.ScrollbarColors
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

        assertEquals(
            scrollState.maxValue,
            scrollState.value,
            "Clicking near the track end with JumpToSpot should jump to the end rather than a single page",
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
        assertEquals(TrackClickBehavior.NextPage, ScrollbarStyle.tabStripWindowsAndLinuxLight().trackClickBehavior)
        assertEquals(TrackClickBehavior.NextPage, ScrollbarStyle.tabStripWindowsAndLinuxDark().trackClickBehavior)
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

        rule.mainClock.autoAdvance = false
        rule.setContent {
            IntUiTheme {
                val state = rememberScrollState()
                scrollState = state
                AlwaysVisibleIndicator(state, clickBehavior = TrackClickBehavior.NextPage)
            }
        }
        rule.waitForIdle()

        val size = rule.onNodeWithTag(TAG).fetchSemanticsNode().size
        // Press below the thumb (which parks at the top), so the initial direction latches downwards (+1).
        rule.onNodeWithTag(TAG).performMouseInput {
            moveTo(Offset(size.width - 4f, size.height * 0.6f))
            press()
        }
        rule.mainClock.advanceTimeBy(16)
        rule.waitForIdle()
        val afterFirstPage = scrollState.value
        assertTrue(afterFirstPage > 0, "The initial press should page downwards")

        // Now move the held pointer above the thumb, so pageDirectionTowardPointer becomes -1 while
        // pagingDirection is latched at +1. pagesBackPastPress must block reverse paging.
        rule.onNodeWithTag(TAG).performMouseInput { moveTo(Offset(size.width - 4f, 4f)) }
        rule.mainClock.advanceTimeBy(1_000)
        rule.waitForIdle()
        val whileHeldAboveThumb = scrollState.value

        rule.onNodeWithTag(TAG).performMouseInput { release() }
        rule.waitForIdle()

        assertEquals(
            afterFirstPage,
            whileHeldAboveThumb,
            "Paging must not reverse when the held pointer crosses to the other side of the thumb",
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
        // Grab near the top of the thumb (y = 6f) and drag to the bottom of the track (size.height - 2f).
        // Because the thumb reaches the bottom when the pointer is still ~thumbHeight above the track end,
        // the bottom ~50px of the track is overshoot.
        rule.onNodeWithTag(TAG).performMouseInput {
            moveTo(Offset(trackX, 6f))
            press()
            moveTo(Offset(trackX, size.height - 2f))
        }
        rule.waitForIdle()
        val atEnd = scrollState.value
        assertEquals(scrollState.maxValue, atEnd, "Dragging to the bottom should pin the content at the end")

        // Move back up within the in-track overshoot margin (still below where the grabbed point sits at maxValue).
        rule.onNodeWithTag(TAG).performMouseInput { moveTo(Offset(trackX, size.height - 15f)) }
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

        val image = rule.onNodeWithTag(TAG).captureToImage().toPixelMap()
        val hasRightTrackPixels = (0 until image.height / 2).any { y -> image[image.width - 4, y] != CONTENT_COLOR }
        val hasBottomTrackPixels = (0 until image.width / 2).any { x -> image[x, image.height - 4] != CONTENT_COLOR }
        assertTrue(hasRightTrackPixels, "The vertical indicator should paint along the right edge")
        assertTrue(hasBottomTrackPixels, "The horizontal indicator should paint along the bottom edge")

        // Click the vertical track and the horizontal track to verify both indicators handle pointer input.
        val size = rule.onNodeWithTag(TAG).fetchSemanticsNode().size
        rule.onNodeWithTag(TAG).performMouseInput {
            moveTo(Offset(size.width - 4f, size.height * 0.7f))
            press()
            release()
        }
        rule.waitForIdle()
        assertTrue(verticalState.value > 0, "Clicking the vertical track should scroll vertically")
        assertEquals(0, horizontalState.value, "Clicking the vertical track must not scroll horizontally")

        rule.onNodeWithTag(TAG).performMouseInput {
            moveTo(Offset(size.width * 0.7f, size.height - 4f))
            press()
            release()
        }
        rule.waitForIdle()
        assertTrue(horizontalState.value > 0, "Clicking the horizontal track should scroll horizontally")
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
        }
        rule.waitForIdle()

        val size = rule.onNodeWithTag(TAG).fetchSemanticsNode().size
        val midScroll = scrollState.maxValue / 2
        assertTrue(midScroll > 0, "The horizontal RTL content should be scrollable")

        for (wheelDelta in listOf(3f, -3f)) {
            rule.runOnIdle { runBlocking { scrollState.scrollTo(midScroll) } }
            rule.onNodeWithTag(TAG).performMouseInput {
                moveTo(Offset(size.width / 2f, size.height / 2f))
                scroll(wheelDelta, ScrollWheel.Horizontal)
            }
            rule.waitForIdle()
            val contentDelta = scrollState.value - midScroll
            assertTrue(contentDelta != 0, "Wheeling ($wheelDelta) over the content should scroll it")

            rule.runOnIdle { runBlocking { scrollState.scrollTo(midScroll) } }
            rule.onNodeWithTag(TAG).performMouseInput {
                moveTo(Offset(size.width / 2f, size.height - 2f))
                scroll(wheelDelta, ScrollWheel.Horizontal)
            }
            rule.waitForIdle()
            val laneDelta = scrollState.value - midScroll

            assertEquals(
                contentDelta,
                laneDelta,
                "The lane wheel must match the content wheel for horizontal RTL (wheel=$wheelDelta)",
            )
        }
    }

    @Test
    fun `the lane wheel matches the content wheel direction in reverse layout`() {
        assumeTrue("The lane is only reserved on macOS", hostOs == OS.MacOS)
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
                        .scrollIndicator(state, style = style, reverseLayout = true)
                ) {
                    Box(Modifier.fillMaxWidth().verticalScroll(state, reverseScrolling = true)) { TallContent() }
                }
            }
        }
        rule.waitForIdle()

        val size = rule.onNodeWithTag(TAG).fetchSemanticsNode().size
        val midScroll = scrollState.maxValue / 2

        for (wheelDelta in listOf(3f, -3f)) {
            rule.runOnIdle { runBlocking { scrollState.scrollTo(midScroll) } }
            rule.onNodeWithTag(TAG).performMouseInput {
                moveTo(Offset(size.width / 2f, size.height / 2f))
                scroll(wheelDelta)
            }
            rule.waitForIdle()
            val contentDelta = scrollState.value - midScroll
            assertTrue(contentDelta != 0, "Wheeling ($wheelDelta) over the reversed content should scroll it")

            rule.runOnIdle { runBlocking { scrollState.scrollTo(midScroll) } }
            rule.onNodeWithTag(TAG).performMouseInput {
                moveTo(Offset(size.width - 2f, size.height / 2f))
                scroll(wheelDelta)
            }
            rule.waitForIdle()
            val laneDelta = scrollState.value - midScroll

            assertEquals(
                contentDelta,
                laneDelta,
                "The lane wheel must match the content wheel in reverseLayout (wheel=$wheelDelta)",
            )
        }
    }

    @Test
    fun `the lane wheel scrolls over the left reserved lane for a vertical RTL indicator`() {
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
        rule.onNodeWithTag(TAG).performMouseInput {
            moveTo(Offset(2f, size.height / 2f))
            scroll(3f)
        }
        rule.waitForIdle()

        assertTrue(scrollState.value > 0, "The wheel over the left RTL lane should scroll the content")
    }

    @Test
    fun `clicking an expanded overlay track does not trigger a clickable child underneath`() {
        var childClicks = 0
        lateinit var scrollState: ScrollState

        rule.setContent {
            IntUiTheme {
                val state = rememberScrollState()
                scrollState = state
                val style = whenScrollingStyle(lingerDuration = 5_000.milliseconds)
                Box(
                    Modifier.size(CONTAINER_SIZE)
                        .background(CONTENT_COLOR)
                        .testTag(TAG)
                        .scrollIndicator(state, style = style)
                ) {
                    Column(Modifier.fillMaxWidth().verticalScroll(state)) {
                        repeat(30) {
                            Box(
                                Modifier.fillMaxWidth().height(20.dp).background(CONTENT_COLOR).clickable {
                                    childClicks++
                                }
                            )
                        }
                    }
                }
            }
        }
        rule.waitForIdle()

        val size = rule.onNodeWithTag(TAG).fetchSemanticsNode().size
        rule.onNodeWithTag(TAG).performMouseInput {
            enter(Offset(size.width - 4f, size.height - 6f))
            moveTo(Offset(size.width - 4f, size.height - 8f))
        }
        rule.waitForIdle()

        rule.onNodeWithTag(TAG).performMouseInput {
            press()
            release()
        }
        rule.waitForIdle()

        assertTrue(scrollState.value > 0, "Clicking the expanded overlay track should scroll the content")
        assertEquals(0, childClicks, "Clicking the expanded overlay track must not click the child underneath")
    }

    @Test
    fun `dragging the thumb does not trigger a clickable or draggable child underneath`() {
        var childClicks = 0
        var childDragDelta = 0f
        lateinit var scrollState: ScrollState

        rule.setContent {
            IntUiTheme {
                val state = rememberScrollState()
                scrollState = state
                val style = whenScrollingStyle(lingerDuration = 5_000.milliseconds)
                Box(
                    Modifier.size(CONTAINER_SIZE)
                        .background(CONTENT_COLOR)
                        .testTag(TAG)
                        .scrollIndicator(state, style = style, keepVisible = true)
                ) {
                    val dragState = rememberDraggableState { childDragDelta += it }
                    Column(Modifier.fillMaxWidth().verticalScroll(state).draggable(dragState, Orientation.Vertical)) {
                        repeat(30) {
                            Box(
                                Modifier.fillMaxWidth().height(20.dp).background(CONTENT_COLOR).clickable {
                                    childClicks++
                                }
                            )
                        }
                    }
                }
            }
        }
        rule.waitForIdle()

        val size = rule.onNodeWithTag(TAG).fetchSemanticsNode().size
        // Reveal the indicator first by hovering its track.
        rule.onNodeWithTag(TAG).performMouseInput {
            enter(Offset(size.width - 4f, 8f))
            moveTo(Offset(size.width - 4f, 10f))
        }
        rule.waitForIdle()

        // Press and drag the thumb over the clickable and draggable content.
        rule.onNodeWithTag(TAG).performMouseInput {
            press()
            moveTo(Offset(size.width - 4f, 80f))
            release()
        }
        rule.waitForIdle()

        assertTrue(scrollState.value > 0, "Dragging the thumb should scroll the content")
        assertEquals(0, childClicks, "Dragging or clicking the thumb must not trigger a clickable child underneath")
        assertEquals(0f, childDragDelta, "Dragging the thumb must not drag a draggable child underneath")
    }

    @Test
    fun `an overlay track waits for expandDelay before taking clicks or painting its expanded background`() {
        var childClicks = 0
        lateinit var scrollState: ScrollState

        rule.mainClock.autoAdvance = false
        rule.setContent {
            IntUiTheme {
                val state = rememberScrollState()
                scrollState = state
                val base = JewelTheme.scrollbarStyle
                val style =
                    remember(base) {
                        ScrollbarStyle(
                            colors =
                                ScrollbarColors(
                                    thumbBackground = base.colors.thumbBackground,
                                    thumbBackgroundActive = base.colors.thumbBackgroundActive,
                                    thumbOpaqueBackground = base.colors.thumbOpaqueBackground,
                                    thumbOpaqueBackgroundHovered = base.colors.thumbOpaqueBackgroundHovered,
                                    thumbBorder = base.colors.thumbBorder,
                                    thumbBorderActive = base.colors.thumbBorderActive,
                                    thumbOpaqueBorder = base.colors.thumbOpaqueBorder,
                                    thumbOpaqueBorderHovered = base.colors.thumbOpaqueBorderHovered,
                                    trackBackground = Color.Transparent,
                                    trackBackgroundExpanded = Color.Blue,
                                    trackOpaqueBackground = base.colors.trackOpaqueBackground,
                                    trackOpaqueBackgroundHovered = base.colors.trackOpaqueBackgroundHovered,
                                ),
                            metrics = base.metrics,
                            trackClickBehavior = TrackClickBehavior.JumpToSpot,
                            scrollbarVisibility =
                                ScrollbarVisibility.WhenScrolling(
                                    trackThickness = 8.dp,
                                    trackThicknessExpanded = 14.dp,
                                    trackPadding = PaddingValues(2.dp),
                                    trackPaddingWithBorder = PaddingValues(2.dp),
                                    trackColorAnimationDuration = Duration.ZERO,
                                    expandAnimationDuration = Duration.ZERO,
                                    thumbColorAnimationDuration = Duration.ZERO,
                                    lingerDuration = 10_000.milliseconds,
                                    expandDelay = 150.milliseconds,
                                    collapseDelay = 300.milliseconds,
                                ),
                        )
                    }
                Box(
                    Modifier.size(CONTAINER_SIZE)
                        .background(CONTENT_COLOR)
                        .testTag(TAG)
                        .scrollIndicator(state, style = style)
                ) {
                    Column(Modifier.fillMaxWidth().verticalScroll(state)) {
                        repeat(30) {
                            Box(
                                Modifier.fillMaxWidth().height(20.dp).background(CONTENT_COLOR).clickable {
                                    childClicks++
                                }
                            )
                        }
                    }
                }
            }
        }
        rule.waitForIdle()

        val size = rule.onNodeWithTag(TAG).fetchSemanticsNode().size
        rule.onNodeWithTag(TAG).performMouseInput {
            enter(Offset(size.width - 4f, size.height - 10f))
            moveTo(Offset(size.width - 4f, size.height - 8f))
        }
        rule.mainClock.advanceTimeBy(100)
        rule.waitForIdle()

        // Before expandDelay (150 ms) elapses, the track is still collapsed: clicking passes through to the child,
        // and the expanded track background is not painted below the thumb.
        val beforeExpandImage = rule.onNodeWithTag(TAG).captureToImage().toPixelMap()
        assertEquals(
            CONTENT_COLOR,
            beforeExpandImage[beforeExpandImage.width - 4, beforeExpandImage.height - 6],
            "The expanded track background must not paint before expandDelay elapses",
        )

        rule.onNodeWithTag(TAG).performMouseInput {
            press()
            release()
        }
        rule.waitForIdle()
        assertEquals(0, scrollState.value, "The overlay track must not take clicks before expandDelay elapses")
        assertEquals(1, childClicks, "Clicks before expandDelay should pass through to the content underneath")

        // Advance past expandDelay: now the track expands, paints its background, and takes clicks.
        rule.mainClock.advanceTimeBy(100)
        rule.waitForIdle()
        val afterExpandImage = rule.onNodeWithTag(TAG).captureToImage().toPixelMap()
        assertEquals(
            Color.Blue,
            afterExpandImage[afterExpandImage.width - 4, afterExpandImage.height - 6],
            "The expanded track background should paint once expandDelay elapses",
        )

        rule.onNodeWithTag(TAG).performMouseInput {
            press()
            release()
        }
        rule.waitForIdle()
        assertTrue(scrollState.value > 0, "Clicking the track after expandDelay should scroll the content")
        assertEquals(1, childClicks, "Clicking the expanded track must not click the child underneath")
    }

    @Test
    fun `leaving the track before expandDelay elapses never expands the track`() {
        rule.mainClock.autoAdvance = false
        rule.setContent {
            IntUiTheme {
                val state = rememberScrollState()
                ExpandingAlwaysVisibleIndicator(state, expandDelay = 150.milliseconds, collapseDelay = 300.milliseconds)
            }
        }
        rule.waitForIdle()
        val collapsed = paintedTrackWidthPx()

        val width = rule.onNodeWithTag(TAG).fetchSemanticsNode().size.width
        rule.onNodeWithTag(TAG).performMouseInput {
            enter(Offset(width - 4f, 40f))
            moveTo(Offset(width - 4f, 42f))
        }
        rule.mainClock.advanceTimeBy(80)
        rule.waitForIdle()
        assertEquals(collapsed, paintedTrackWidthPx(), "The track should still be collapsed at 80 ms")

        // Leave the track before the 150 ms expandDelay finishes.
        rule.onNodeWithTag(TAG).performMouseInput { moveTo(Offset(10f, 40f)) }
        rule.mainClock.advanceTimeBy(200)
        rule.waitForIdle()
        assertEquals(collapsed, paintedTrackWidthPx(), "Leaving before expandDelay must cancel the expansion")
    }

    @Test
    fun `re-entering the track before collapseDelay elapses keeps the track expanded`() {
        rule.mainClock.autoAdvance = false
        rule.setContent {
            IntUiTheme {
                val state = rememberScrollState()
                ExpandingAlwaysVisibleIndicator(state, expandDelay = 150.milliseconds, collapseDelay = 300.milliseconds)
            }
        }
        rule.waitForIdle()
        val collapsed = paintedTrackWidthPx()

        val width = rule.onNodeWithTag(TAG).fetchSemanticsNode().size.width
        rule.onNodeWithTag(TAG).performMouseInput {
            enter(Offset(width - 4f, 40f))
            moveTo(Offset(width - 4f, 42f))
        }
        rule.mainClock.advanceTimeBy(200)
        rule.waitForIdle()
        val expanded = paintedTrackWidthPx()
        assertTrue(expanded > collapsed, "The track should be expanded after expandDelay")

        // Leave the track briefly (100 ms < 300 ms collapseDelay), then re-enter.
        rule.onNodeWithTag(TAG).performMouseInput { moveTo(Offset(10f, 40f)) }
        rule.mainClock.advanceTimeBy(100)
        rule.waitForIdle()
        rule.onNodeWithTag(TAG).performMouseInput { moveTo(Offset(width - 4f, 42f)) }
        rule.mainClock.advanceTimeBy(250)
        rule.waitForIdle()

        assertEquals(expanded, paintedTrackWidthPx(), "Re-entering before collapseDelay must keep the track expanded")
    }

    @Test
    fun `disabling a hovered indicator clears hover and collapses the track`() {
        var enabled by mutableStateOf(true)

        rule.setContent {
            IntUiTheme {
                val state = rememberScrollState()
                val base = JewelTheme.scrollbarStyle
                val style =
                    remember(base) {
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
                    }
                Box(
                    Modifier.size(CONTAINER_SIZE)
                        .background(CONTENT_COLOR)
                        .testTag(TAG)
                        .scrollIndicator(state, style = style, enabled = enabled)
                ) {
                    Box(Modifier.fillMaxWidth().verticalScroll(state)) { TallContent() }
                }
            }
        }
        rule.waitForIdle()
        val collapsed = paintedTrackWidthPx()

        val width = rule.onNodeWithTag(TAG).fetchSemanticsNode().size.width
        rule.onNodeWithTag(TAG).performMouseInput {
            enter(Offset(width - 4f, 40f))
            moveTo(Offset(width - 4f, 42f))
        }
        rule.waitForIdle()
        val expanded = paintedTrackWidthPx()
        assertTrue(expanded > collapsed, "Hovering should expand the enabled indicator")

        // Disable while hovered, then move the pointer away while disabled and re-enable.
        enabled = false
        rule.waitForIdle()
        assertEquals(collapsed, paintedTrackWidthPx(), "Disabling the indicator must collapse the track")

        rule.onNodeWithTag(TAG).performMouseInput { exit(Offset(-10f, -10f)) }
        enabled = true
        rule.waitForIdle()
        assertEquals(collapsed, paintedTrackWidthPx(), "Re-enabling with the pointer outside must stay collapsed")
    }

    @Test
    fun `pressing the thumb interrupts an active programmatic scroll animation`() {
        lateinit var scrollState: ScrollState
        lateinit var scope: CoroutineScope

        rule.mainClock.autoAdvance = false
        rule.setContent {
            IntUiTheme {
                val state = rememberScrollState()
                scrollState = state
                scope = rememberCoroutineScope()
                AlwaysVisibleIndicator(state)
            }
        }
        rule.waitForIdle()

        // Start a long programmatic scroll animation towards the bottom.
        rule.runOnIdle {
            scope.launch {
                scrollState.animateScrollTo(scrollState.maxValue, tween(durationMillis = 5_000, easing = LinearEasing))
            }
        }
        rule.mainClock.advanceTimeBy(500)
        rule.waitForIdle()
        val midAnimation = scrollState.value
        assertTrue(
            midAnimation > 0 && midAnimation < scrollState.maxValue,
            "The animation should be in flight (value=$midAnimation, max=${scrollState.maxValue})",
        )

        // Grab the thumb and hold it still while the clock advances: the animation must be cancelled.
        val size = rule.onNodeWithTag(TAG).fetchSemanticsNode().size
        val thumb = thumbBounds() ?: error("The thumb should be drawn")
        val grabY = (thumb.first + thumb.second) / 2f
        rule.onNodeWithTag(TAG).performMouseInput { moveTo(Offset(size.width - 4f, grabY)) }
        val beforePress = scrollState.value
        rule.onNodeWithTag(TAG).performMouseInput { press() }
        rule.mainClock.advanceTimeBy(1_000)
        rule.waitForIdle()
        val whileHeld = scrollState.value

        rule.onNodeWithTag(TAG).performMouseInput { release() }
        rule.mainClock.advanceTimeBy(1_000)
        rule.waitForIdle()

        assertEquals(
            beforePress,
            whileHeld,
            "Pressing the thumb must cancel the in-flight scroll animation immediately",
        )
        assertEquals(whileHeld, scrollState.value, "The cancelled animation must not resume after releasing the thumb")
    }

    @Test
    fun `growing the content while holding the track resumes NextPage paging`() {
        lateinit var scrollState: ScrollState
        val itemCount = mutableIntStateOf(20)

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
                        Column {
                            repeat(itemCount.intValue) {
                                Box(Modifier.fillMaxWidth().height(20.dp).background(CONTENT_COLOR))
                            }
                        }
                    }
                }
            }
        }
        rule.waitForIdle()

        val size = rule.onNodeWithTag(TAG).fetchSemanticsNode().size
        rule.onNodeWithTag(TAG).performMouseInput {
            moveTo(Offset(size.width - 6f, size.height - 2f))
            press()
        }
        rule.mainClock.advanceTimeBy(2_000)
        rule.waitForIdle()
        val initialLimit = scrollState.value
        assertEquals(scrollState.maxValue, initialLimit, "Paging should reach the initial end of the content")

        // Grow the content while the press is still held at the bottom of the track.
        itemCount.intValue = 60
        rule.mainClock.advanceTimeBy(2_000)
        rule.waitForIdle()
        val afterGrowth = scrollState.value
        rule.onNodeWithTag(TAG).performMouseInput { release() }

        assertTrue(
            afterGrowth > initialLimit,
            "Growing the content while holding the track should resume paging past $initialLimit, got $afterGrowth",
        )
    }

    @Test
    fun `the thumb fades out over thumbColorAnimationDuration instead of disappearing immediately`() {
        rule.mainClock.autoAdvance = false
        rule.setContent {
            IntUiTheme {
                val state = rememberScrollState()
                val base = JewelTheme.scrollbarStyle
                val style =
                    remember(base) {
                        ScrollbarStyle(
                            colors = base.colors,
                            metrics = base.metrics,
                            trackClickBehavior = TrackClickBehavior.JumpToSpot,
                            scrollbarVisibility =
                                ScrollbarVisibility.WhenScrolling(
                                    trackThickness = 8.dp,
                                    trackThicknessExpanded = 14.dp,
                                    trackPadding = PaddingValues(2.dp),
                                    trackPaddingWithBorder = PaddingValues(2.dp),
                                    trackColorAnimationDuration = Duration.ZERO,
                                    expandAnimationDuration = Duration.ZERO,
                                    thumbColorAnimationDuration = 300.milliseconds,
                                    lingerDuration = 200.milliseconds,
                                    expandDelay = Duration.ZERO,
                                    collapseDelay = Duration.ZERO,
                                ),
                        )
                    }
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
        rule.mainClock.advanceTimeBy(32)
        rule.waitForIdle()
        assertTrue(hasIndicatorPixels(), "Hovering should reveal the indicator")

        rule.onNodeWithTag(TAG).performMouseInput { exit(Offset(-10f, -10f)) }
        // Advance past lingerDuration (200 ms) into the middle of thumbColorAnimationDuration (300 ms).
        rule.mainClock.advanceTimeBy(300)
        rule.waitForIdle()
        assertTrue(hasIndicatorPixels(), "The indicator should still be drawn while fading out after lingerDuration")

        // Advance past the end of the linger and thumbColorAnimationDuration.
        rule.mainClock.advanceTimeBy(600)
        rule.waitForIdle()
        assertFalse(hasIndicatorPixels(), "The indicator should finish fading out once the animation completes")
    }

    @Test
    fun `a non-scrollable always-visible indicator does not narrow the content until content overflows`() {
        val itemCount = mutableIntStateOf(3)
        lateinit var scrollState: ScrollState

        rule.setContent {
            IntUiTheme {
                val state = rememberScrollState()
                scrollState = state
                val style = alwaysVisibleStyle()
                Box(Modifier.size(CONTAINER_SIZE).testTag(TAG).scrollIndicator(state, style = style)) {
                    Column(Modifier.fillMaxWidth().testTag(CONTENT_TAG).verticalScroll(state)) {
                        repeat(itemCount.intValue) {
                            Box(Modifier.fillMaxWidth().height(20.dp).background(CONTENT_COLOR))
                        }
                    }
                }
            }
        }
        rule.waitForIdle()

        assertFalse(scrollState.canScrollForward, "3 items fit in the 200.dp container without scrolling")
        val container = rule.onNodeWithTag(TAG).fetchSemanticsNode().size
        val nonScrollableContent = rule.onNodeWithTag(CONTENT_TAG).fetchSemanticsNode().size
        assertEquals(
            container.width,
            nonScrollableContent.width,
            "Non-scrollable content must not have a scrollbar lane carved out of its width",
        )

        itemCount.intValue = 30
        rule.waitForIdle()
        assertTrue(scrollState.canScrollForward, "30 items should overflow the container")
        val expectedReserved = if (hostOs == OS.MacOS) with(rule.density) { TRACK_THICKNESS.roundToPx() } else 0
        val scrollableContent = rule.onNodeWithTag(CONTENT_TAG).fetchSemanticsNode().size
        assertEquals(
            container.width - expectedReserved,
            scrollableContent.width,
            "Once the content can scroll, macOS AlwaysVisible should reserve its lane",
        )
    }

    @Test
    fun `works with a lazy grid state`() {
        lateinit var gridState: LazyGridState

        rule.setContent {
            IntUiTheme {
                val state = rememberLazyGridState()
                gridState = state
                val style = alwaysVisibleStyle()
                Box(
                    Modifier.size(CONTAINER_SIZE)
                        .background(CONTENT_COLOR)
                        .testTag(TAG)
                        .scrollIndicator(state, style = style)
                ) {
                    LazyVerticalGrid(columns = GridCells.Fixed(2), state = state, modifier = Modifier.fillMaxSize()) {
                        items(60) { Box(Modifier.fillMaxWidth().height(24.dp).background(CONTENT_COLOR)) }
                    }
                }
            }
        }
        rule.waitForIdle()

        assertTrue(gridState.canScrollForward, "The lazy grid should be scrollable")
        assertTrue(hasIndicatorPixels(), "The indicator should draw for LazyGridState")

        val size = rule.onNodeWithTag(TAG).fetchSemanticsNode().size
        rule.onNodeWithTag(TAG).performMouseInput {
            moveTo(Offset(size.width - 4f, size.height - 6f))
            press()
            release()
        }
        rule.waitForIdle()
        assertTrue(gridState.firstVisibleItemIndex > 0, "Clicking the track should scroll the lazy grid")
    }

    @Test
    fun `works with a lazy staggered grid state`() {
        lateinit var gridState: LazyStaggeredGridState

        rule.setContent {
            IntUiTheme {
                val state = rememberLazyStaggeredGridState()
                gridState = state
                val style = alwaysVisibleStyle()
                Box(
                    Modifier.size(CONTAINER_SIZE)
                        .background(CONTENT_COLOR)
                        .testTag(TAG)
                        .scrollIndicator(state, style = style)
                ) {
                    LazyVerticalStaggeredGrid(
                        columns = StaggeredGridCells.Fixed(2),
                        state = state,
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        items(60) { Box(Modifier.fillMaxWidth().height(24.dp).background(CONTENT_COLOR)) }
                    }
                }
            }
        }
        rule.waitForIdle()

        assertTrue(gridState.canScrollForward, "The staggered grid should be scrollable")
        assertTrue(hasIndicatorPixels(), "The indicator should draw for LazyStaggeredGridState")
    }

    @Test
    fun `works with a pager state`() {
        lateinit var pagerState: PagerState

        rule.setContent {
            IntUiTheme {
                val state = rememberPagerState(pageCount = { 10 })
                pagerState = state
                val style = alwaysVisibleStyle()
                Box(
                    Modifier.size(CONTAINER_SIZE)
                        .background(CONTENT_COLOR)
                        .testTag(TAG)
                        .scrollIndicator(state, style = style)
                ) {
                    VerticalPager(state = state, modifier = Modifier.fillMaxSize()) {
                        Box(Modifier.fillMaxSize().background(CONTENT_COLOR))
                    }
                }
            }
        }
        rule.waitForIdle()

        assertTrue(pagerState.canScrollForward, "The pager should be scrollable")
        assertTrue(hasIndicatorPixels(), "The indicator should draw for PagerState")
    }

    @Test
    fun `reverseLayout with a plain ScrollState parks the thumb at the end and scrolls via drag and track click`() {
        lateinit var scrollState: ScrollState

        rule.setContent {
            IntUiTheme {
                val state = rememberScrollState()
                scrollState = state
                val style = alwaysVisibleStyle(TrackClickBehavior.JumpToSpot)
                Box(
                    Modifier.size(CONTAINER_SIZE)
                        .background(CONTENT_COLOR)
                        .testTag(TAG)
                        .scrollIndicator(state, style = style, reverseLayout = true)
                ) {
                    Box(Modifier.fillMaxWidth().verticalScroll(state, reverseScrolling = true)) { TallContent() }
                }
            }
        }
        rule.waitForIdle()
        assertEquals(0, scrollState.value, "The reversed ScrollState should start at 0")

        // At rest (value == 0), a reversed ScrollState must park its thumb at the bottom of the track.
        val image = rule.onNodeWithTag(TAG).captureToImage().toPixelMap()
        val trackX = image.width - 4
        assertTrue(image[trackX, image.height - 8] != CONTENT_COLOR, "The thumb should sit at the track end")
        assertEquals(CONTENT_COLOR, image[trackX, 8], "The thumb should not be at the track start")

        // Drag the thumb upwards from the bottom toward the middle.
        val size = rule.onNodeWithTag(TAG).fetchSemanticsNode().size
        rule.onNodeWithTag(TAG).performMouseInput {
            moveTo(Offset(size.width - 4f, size.height - 10f))
            press()
            moveTo(Offset(size.width - 4f, size.height / 2f))
            release()
        }
        rule.waitForIdle()
        val afterDrag = scrollState.value
        assertTrue(afterDrag > 0, "Dragging the reversed ScrollState thumb upwards should increase scrollState.value")

        // Click near the top of the track to jump all the way to maxValue.
        rule.onNodeWithTag(TAG).performMouseInput {
            moveTo(Offset(size.width - 4f, 6f))
            press()
            release()
        }
        rule.waitForIdle()
        assertEquals(
            scrollState.maxValue,
            scrollState.value,
            "Clicking near the top of a reversed track should jump to maxValue",
        )
    }

    @Test
    fun `replacing the scrollState instance observes the new state for WhenScrolling visibility`() {
        val stateA = ScrollState(initial = 0)
        val stateB = ScrollState(initial = 0)
        var useSecondState by mutableStateOf(false)
        lateinit var scope: CoroutineScope

        rule.mainClock.autoAdvance = false
        rule.setContent {
            IntUiTheme {
                scope = rememberCoroutineScope()
                val activeState = if (useSecondState) stateB else stateA
                val style = whenScrollingStyle(lingerDuration = 500.milliseconds)
                Box(
                    Modifier.size(CONTAINER_SIZE)
                        .background(CONTENT_COLOR)
                        .testTag(TAG)
                        .scrollIndicator(activeState, style = style)
                ) {
                    Box(Modifier.fillMaxWidth().verticalScroll(activeState)) { TallContent() }
                }
            }
        }
        rule.waitForIdle()
        assertFalse(hasIndicatorPixels(), "The indicator should start hidden")

        // Switch to stateB and start an animated scroll on stateB.
        useSecondState = true
        rule.mainClock.advanceTimeBy(16)
        rule.waitForIdle()

        rule.runOnIdle {
            scope.launch { stateB.animateScrollTo(200, tween(durationMillis = 1_000, easing = LinearEasing)) }
        }
        rule.mainClock.advanceTimeBy(100)
        rule.waitForIdle()

        assertTrue(hasIndicatorPixels(), "Scrolling the replaced scrollState should reveal the indicator")
    }

    @Test
    fun `pointer movement prolongs the linger by a single lingerDuration and does not recompose the caller`() {
        var callerRecompositions = 0

        rule.mainClock.autoAdvance = false
        rule.setContent {
            IntUiTheme {
                val state = rememberScrollState()
                val style = whenScrollingStyle(lingerDuration = 400.milliseconds)
                callerRecompositions++
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
        val initialRecompositions = callerRecompositions

        val width = rule.onNodeWithTag(TAG).fetchSemanticsNode().size.width
        rule.onNodeWithTag(TAG).performMouseInput { enter(Offset(width - 4f, 40f)) }
        rule.mainClock.advanceTimeBy(32)
        rule.waitForIdle()
        assertTrue(hasIndicatorPixels(), "Hovering the track should reveal the indicator")

        // Move off the track onto the content so hover is false and the linger starts.
        rule.onNodeWithTag(TAG).performMouseInput { moveTo(Offset(20f, 40f)) }
        rule.mainClock.advanceTimeBy(64)
        rule.waitForIdle()
        val recompositionsAfterHoverExit = callerRecompositions

        // Move the pointer several times over the content; this must not recompose the caller.
        repeat(5) { step ->
            rule.onNodeWithTag(TAG).performMouseInput { moveTo(Offset(20f + step * 5f, 50f + step * 5f)) }
            rule.mainClock.advanceTimeBy(50)
            rule.waitForIdle()
        }
        assertEquals(
            recompositionsAfterHoverExit,
            callerRecompositions,
            "Moving the pointer over the content must not recompose the caller (started at $initialRecompositions)",
        )
        assertTrue(hasIndicatorPixels(), "Pointer movement should keep the indicator visible")

        // Stop moving: the indicator must hide after 1 * lingerDuration (400 ms), not 2 * lingerDuration (800 ms).
        rule.mainClock.advanceTimeBy(300)
        rule.waitForIdle()
        assertTrue(hasIndicatorPixels(), "The indicator should still be visible at 300 ms (< 400 ms lingerDuration)")

        rule.mainClock.advanceTimeBy(150)
        rule.waitForIdle()
        assertFalse(hasIndicatorPixels(), "The indicator must hide after a single 400 ms lingerDuration (at 450 ms)")
    }

    @Test
    fun `collapsing track fades out trackBackground over trackColorAnimationDuration`() {
        rule.mainClock.autoAdvance = false
        rule.setContent {
            IntUiTheme {
                val state = rememberScrollState()
                val base = JewelTheme.scrollbarStyle
                val style =
                    remember(base) {
                        ScrollbarStyle(
                            colors =
                                ScrollbarColors(
                                    thumbBackground = base.colors.thumbBackground,
                                    thumbBackgroundActive = base.colors.thumbBackgroundActive,
                                    thumbOpaqueBackground = base.colors.thumbOpaqueBackground,
                                    thumbOpaqueBackgroundHovered = base.colors.thumbOpaqueBackgroundHovered,
                                    thumbBorder = base.colors.thumbBorder,
                                    thumbBorderActive = base.colors.thumbBorderActive,
                                    thumbOpaqueBorder = base.colors.thumbOpaqueBorder,
                                    thumbOpaqueBorderHovered = base.colors.thumbOpaqueBorderHovered,
                                    trackBackground = Color.Transparent,
                                    trackBackgroundExpanded = Color.Blue,
                                    trackOpaqueBackground = base.colors.trackOpaqueBackground,
                                    trackOpaqueBackgroundHovered = base.colors.trackOpaqueBackgroundHovered,
                                ),
                            metrics = base.metrics,
                            trackClickBehavior = TrackClickBehavior.JumpToSpot,
                            scrollbarVisibility =
                                ScrollbarVisibility.WhenScrolling(
                                    trackThickness = 8.dp,
                                    trackThicknessExpanded = 14.dp,
                                    trackPadding = PaddingValues(2.dp),
                                    trackPaddingWithBorder = PaddingValues(2.dp),
                                    trackColorAnimationDuration = 300.milliseconds,
                                    expandAnimationDuration = Duration.ZERO,
                                    thumbColorAnimationDuration = Duration.ZERO,
                                    lingerDuration = 10_000.milliseconds,
                                    expandDelay = Duration.ZERO,
                                    collapseDelay = Duration.ZERO,
                                ),
                        )
                    }
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

        val size = rule.onNodeWithTag(TAG).fetchSemanticsNode().size
        rule.onNodeWithTag(TAG).performMouseInput {
            enter(Offset(size.width - 4f, size.height - 10f))
            moveTo(Offset(size.width - 4f, size.height - 8f))
        }
        rule.mainClock.advanceTimeBy(400)
        rule.waitForIdle()
        val expandedImage = rule.onNodeWithTag(TAG).captureToImage().toPixelMap()
        assertEquals(
            Color.Blue,
            expandedImage[expandedImage.width - 4, expandedImage.height - 6],
            "The expanded track should be solid Blue",
        )

        // Leave the track: at 150 ms (halfway through the 300 ms trackColorAnimationDuration), the track background
        // must still be fading out rather than cut off immediately on the first frame of collapse.
        rule.onNodeWithTag(TAG).performMouseInput { moveTo(Offset(10f, 40f)) }
        rule.mainClock.advanceTimeBy(150)
        rule.waitForIdle()
        val midFadeImage = rule.onNodeWithTag(TAG).captureToImage().toPixelMap()
        val midFadePixel = midFadeImage[midFadeImage.width - 4, midFadeImage.height - 6]
        assertTrue(
            midFadePixel != CONTENT_COLOR && midFadePixel != Color.Blue,
            "The track background should be mid-fade at 150 ms, but pixel was $midFadePixel",
        )

        // Once the 300 ms fade completes, the lower track pixel returns to CONTENT_COLOR.
        rule.mainClock.advanceTimeBy(250)
        rule.waitForIdle()
        val collapsedImage = rule.onNodeWithTag(TAG).captureToImage().toPixelMap()
        assertEquals(
            CONTENT_COLOR,
            collapsedImage[collapsedImage.width - 4, collapsedImage.height - 6],
            "The track background should be gone once trackColorAnimationDuration finishes",
        )
    }

    @Test
    fun `holding a track press while drifting outside the track keeps the indicator expanded and visible`() {
        lateinit var scrollState: ScrollState

        rule.mainClock.autoAdvance = false
        rule.setContent {
            IntUiTheme {
                val state = rememberScrollState()
                scrollState = state
                val style =
                    whenScrollingStyle(
                        lingerDuration = 200.milliseconds,
                        expandDelay = Duration.ZERO,
                        collapseDelay = 100.milliseconds,
                    )
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

        val size = rule.onNodeWithTag(TAG).fetchSemanticsNode().size
        rule.onNodeWithTag(TAG).performMouseInput {
            enter(Offset(size.width - 4f, size.height - 10f))
            moveTo(Offset(size.width - 4f, size.height - 8f))
        }
        rule.mainClock.advanceTimeBy(32)
        rule.waitForIdle()
        val expandedWidth = paintedTrackWidthPx()
        assertTrue(expandedWidth > 0, "Hovering should reveal and expand the overlay indicator")

        // Press the track and drag the held pointer outside the track bounds (x = 10f), then wait past both
        // collapseDelay (100 ms) and lingerDuration (200 ms) without moving.
        rule.onNodeWithTag(TAG).performMouseInput {
            press()
            moveTo(Offset(10f, size.height / 2f))
        }
        rule.mainClock.advanceTimeBy(600)
        rule.waitForIdle()

        assertEquals(
            expandedWidth,
            paintedTrackWidthPx(),
            "Holding a track press outside the track bounds must keep the indicator visible and expanded",
        )

        // While still held outside the track, moving vertically must continue to follow the JumpToSpot press.
        rule.onNodeWithTag(TAG).performMouseInput { moveTo(Offset(10f, 4f)) }
        rule.mainClock.advanceTimeBy(32)
        rule.waitForIdle()
        assertEquals(0, scrollState.value, "Moving the held track press to the top should jump back to 0")

        rule.onNodeWithTag(TAG).performMouseInput { release() }
        rule.mainClock.advanceTimeBy(600)
        rule.waitForIdle()
        assertFalse(hasIndicatorPixels(), "Releasing the track press outside the track should let the indicator hide")
    }

    @Test
    fun `dragging the thumb while PreventUserInput is active does not bypass the mutex`() {
        lateinit var scrollState: ScrollState
        lateinit var scope: CoroutineScope

        rule.setContent {
            IntUiTheme {
                val state = rememberScrollState()
                scrollState = state
                scope = rememberCoroutineScope()
                AlwaysVisibleIndicator(state)
            }
        }
        rule.waitForIdle()

        lateinit var blockJob: Job
        rule.runOnIdle {
            blockJob =
                scope.launch(start = kotlinx.coroutines.CoroutineStart.UNDISPATCHED) {
                    scrollState.scroll(MutatePriority.PreventUserInput) { awaitCancellation() }
                }
        }

        val size = rule.onNodeWithTag(TAG).fetchSemanticsNode().size
        rule.onNodeWithTag(TAG).performMouseInput {
            moveTo(Offset(size.width - 4f, 8f))
            press()
            moveTo(Offset(size.width - 4f, 80f))
            release()
        }
        rule.waitForIdle()

        assertEquals(
            0,
            scrollState.value,
            "Dragging the thumb while PreventUserInput is held must not bypass MutatorMutex",
        )
        rule.runOnIdle { blockJob.cancel() }
    }

    @Test
    fun `shrinking content to fit the viewport mid-drag cancels the active drag gesture`() {
        lateinit var scrollState: ScrollState
        val itemCount = mutableIntStateOf(30)

        rule.setContent {
            IntUiTheme {
                val state = rememberScrollState()
                scrollState = state
                val style = alwaysVisibleStyle()
                Box(
                    Modifier.size(CONTAINER_SIZE)
                        .background(CONTENT_COLOR)
                        .testTag(TAG)
                        .scrollIndicator(state, style = style)
                ) {
                    Column(Modifier.fillMaxWidth().verticalScroll(state)) {
                        repeat(itemCount.intValue) {
                            Box(Modifier.fillMaxWidth().height(20.dp).background(CONTENT_COLOR))
                        }
                    }
                }
            }
        }
        rule.waitForIdle()

        val size = rule.onNodeWithTag(TAG).fetchSemanticsNode().size
        val trackX = size.width - 4f
        rule.onNodeWithTag(TAG).performMouseInput {
            moveTo(Offset(trackX, 8f))
            press()
            moveTo(Offset(trackX, 40f))
        }
        rule.waitForIdle()
        assertTrue(scrollState.value > 0, "Dragging the thumb should scroll the content")
        assertTrue(scrollState.isScrollInProgress, "The drag session should hold isScrollInProgress")

        // Shrink the content so it is no longer scrollable while the mouse button is still held down.
        itemCount.intValue = 2
        rule.waitForIdle()
        assertFalse(scrollState.isScrollInProgress, "Losing scrollability must cancel the drag scroll session")
        assertFalse(hasIndicatorPixels(), "Non-scrollable content must not draw the indicator")

        // Grow the content back while the mouse is still held: the cancelled drag must not resume without a new press.
        itemCount.intValue = 30
        rule.waitForIdle()
        val valueAfterRegrow = scrollState.value
        rule.onNodeWithTag(TAG).performMouseInput {
            moveTo(Offset(trackX, 120f))
            release()
        }
        rule.waitForIdle()
        assertEquals(
            valueAfterRegrow,
            scrollState.value,
            "Moving after a cancelled drag must not scroll until a new gesture starts",
        )
    }

    @Test
    fun `an unattached ScrollState does not reserve a lane on macOS`() {
        rule.setContent {
            IntUiTheme {
                val unattachedState = remember { ScrollState(initial = 0) }
                val style = alwaysVisibleStyle()
                Box(Modifier.size(CONTAINER_SIZE).testTag(TAG).scrollIndicator(unattachedState, style = style)) {
                    Box(Modifier.fillMaxSize().testTag(CONTENT_TAG))
                }
            }
        }
        rule.waitForIdle()

        val container = rule.onNodeWithTag(TAG).fetchSemanticsNode().size
        val content = rule.onNodeWithTag(CONTENT_TAG).fetchSemanticsNode().size
        assertEquals(
            container.width,
            content.width,
            "An unmeasured ScrollState that is not attached to verticalScroll must not reserve a scrollbar lane",
        )
    }

    @Test
    fun `re-enabling an initially disabled indicator reveals and holds it visible on hover`() {
        val enabled = mutableStateOf(false)

        rule.mainClock.autoAdvance = false
        rule.setContent {
            IntUiTheme {
                val state = rememberScrollState()
                val style = whenScrollingStyle(lingerDuration = 200.milliseconds)
                Box(
                    Modifier.size(CONTAINER_SIZE)
                        .background(CONTENT_COLOR)
                        .testTag(TAG)
                        .scrollIndicator(state, style = style, enabled = enabled.value)
                ) {
                    Box(Modifier.fillMaxWidth().verticalScroll(state)) { TallContent() }
                }
            }
        }
        rule.waitForIdle()

        enabled.value = true
        rule.mainClock.advanceTimeBy(16)
        rule.waitForIdle()

        val size = rule.onNodeWithTag(TAG).fetchSemanticsNode().size
        rule.onNodeWithTag(TAG).performMouseInput { moveTo(Offset(size.width - 2f, 20f)) }
        rule.mainClock.advanceTimeBy(500)
        rule.waitForIdle()

        assertTrue(
            hasIndicatorPixels(),
            "Hovering the track after toggling enabled from false to true must reveal and hold the indicator visible",
        )
    }

    @Test
    fun `expanding a bordered WhenScrolling indicator keeps trackPaddingWithBorder`() {
        rule.mainClock.autoAdvance = false
        rule.setContent {
            IntUiTheme {
                val state = rememberScrollState()
                val base = JewelTheme.scrollbarStyle
                val style =
                    remember(base) {
                        ScrollbarStyle(
                            colors =
                                ScrollbarColors(
                                    thumbBackground = Color.Transparent,
                                    thumbBackgroundActive = Color.Green,
                                    thumbOpaqueBackground = Color.Green,
                                    thumbOpaqueBackgroundHovered = Color.Green,
                                    thumbBorder = Color.Transparent,
                                    thumbBorderActive = Color.Blue,
                                    thumbOpaqueBorder = Color.Blue,
                                    thumbOpaqueBorderHovered = Color.Blue,
                                    trackBackground = Color.Transparent,
                                    trackBackgroundExpanded = Color.Transparent,
                                    trackOpaqueBackground = Color.Transparent,
                                    trackOpaqueBackgroundHovered = Color.Transparent,
                                ),
                            metrics = base.metrics,
                            trackClickBehavior = TrackClickBehavior.JumpToSpot,
                            scrollbarVisibility =
                                ScrollbarVisibility.WhenScrolling(
                                    trackThickness = 11.dp,
                                    trackThicknessExpanded = 14.dp,
                                    trackPadding = PaddingValues(2.dp),
                                    trackPaddingWithBorder = PaddingValues(1.dp),
                                    trackColorAnimationDuration = Duration.ZERO,
                                    expandAnimationDuration = Duration.ZERO,
                                    thumbColorAnimationDuration = Duration.ZERO,
                                    lingerDuration = 5_000.milliseconds,
                                ),
                        )
                    }
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

        val size = rule.onNodeWithTag(TAG).fetchSemanticsNode().size
        rule.onNodeWithTag(TAG).performMouseInput { moveTo(Offset(size.width - 2f, 20f)) }
        rule.mainClock.advanceTimeBy(64)
        rule.waitForIdle()

        // 14dp track - 2 * 1dp trackPaddingWithBorder = 12dp painted thumb (1dp border + 10dp fill + 1dp border).
        val image = rule.onNodeWithTag(TAG).captureToImage().toPixelMap()
        val paintedCols = (0 until image.width).count { x -> image[x, 30] != CONTENT_COLOR }
        assertEquals(
            12,
            paintedCols,
            "An expanded bordered WhenScrolling thumb should use trackPaddingWithBorder (1dp -> 12px painted), not trackPadding (2dp -> 10px)",
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
