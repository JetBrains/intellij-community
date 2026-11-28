// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jewel.ui.component

import androidx.compose.foundation.MutatePriority
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.foundation.v2.ScrollbarAdapter
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ScrollWheel
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertHeightIsEqualTo
import androidx.compose.ui.test.assertLeftPositionInRootIsEqualTo
import androidx.compose.ui.test.assertTopPositionInRootIsEqualTo
import androidx.compose.ui.test.assertWidthIsEqualTo
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onParent
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.time.Duration
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import org.jetbrains.jewel.foundation.scroll.Scroll2DState
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.intui.standalone.styling.default
import org.jetbrains.jewel.intui.standalone.theme.IntUiTheme
import org.jetbrains.jewel.ui.component.styling.ScrollbarStyle
import org.jetbrains.jewel.ui.component.styling.ScrollbarVisibility
import org.jetbrains.jewel.ui.component.styling.TrackClickBehavior
import org.jetbrains.jewel.ui.theme.scrollbarStyle
import org.jetbrains.skiko.OS
import org.jetbrains.skiko.hostOs
import org.junit.Rule
import org.junit.Test

@Suppress("LargeClass") // Big test suite is big
class Scrollable2DContainerTest {
    @get:Rule val rule = createComposeRule()

    @Test
    fun `the state reports the scroll range of the two axes`() {
        val state = Scroll2DState()
        rule.setContent { OverlayContainer(state) }
        rule.waitForIdle()

        val viewport = with(rule.density) { IntSize(VIEWPORT.roundToPx(), VIEWPORT.roundToPx()) }
        val content = with(rule.density) { IntSize(CONTENT_WIDTH.roundToPx(), CONTENT_HEIGHT.roundToPx()) }

        assertEquals(viewport, state.viewportSize)
        assertEquals(IntOffset(content.width - viewport.width, content.height - viewport.height), state.maxValue)
        assertEquals(IntOffset.Zero, state.value)
    }

    @Test
    fun `content that fits cannot scroll`() {
        val state = Scroll2DState()
        rule.setContent { OverlayContainer(state, contentWidth = 50.dp, contentHeight = 50.dp) }
        rule.waitForIdle()

        assertEquals(IntOffset.Zero, state.maxValue)
        assertFalse(state.canScroll(Offset(10f, 10f)))
        assertFalse(state.canScroll(Offset(-10f, -10f)))
        assertFalse(state.horizontalScrollableState.canScrollForward)
        assertFalse(state.verticalScrollableState.canScrollForward)
    }

    @Test
    fun `the vertical mouse wheel scrolls the vertical axis only`() {
        val state = Scroll2DState()
        rule.setContent { OverlayContainer(state) }

        rule.onNodeWithTag(CONTAINER_TAG).performMouseInput { scroll(3f, ScrollWheel.Vertical) }
        rule.waitForIdle()

        assertTrue(state.value.y > 0, "Expected a vertical scroll, but the value is ${state.value}")
        assertEquals(0, state.value.x)
    }

    @Test
    fun `the horizontal mouse wheel scrolls the horizontal axis only`() {
        val state = Scroll2DState()
        rule.setContent { OverlayContainer(state) }

        rule.onNodeWithTag(CONTAINER_TAG).performMouseInput { scroll(3f, ScrollWheel.Horizontal) }
        rule.waitForIdle()

        assertTrue(state.value.x > 0, "Expected a horizontal scroll, but the value is ${state.value}")
        assertEquals(0, state.value.y)
    }

    @Test
    fun `a diagonal drag scrolls the two axes`() {
        val state = Scroll2DState()
        rule.setContent { OverlayContainer(state) }

        rule.onNodeWithTag(CONTAINER_TAG).performTouchInput { swipe(center, center - Offset(60f, 60f)) }
        rule.waitForIdle()

        assertTrue(
            state.value.x > 0 && state.value.y > 0,
            "Expected a diagonal scroll, but the value is ${state.value}",
        )
    }

    @Test
    fun `the content moves by the scroll value`() {
        val state = Scroll2DState()
        rule.setContent { OverlayContainer(state) }

        runBlocking { state.scrollTo(IntOffset(30, 40)) }
        rule.waitForIdle()

        with(rule.density) {
            rule.onNodeWithTag(CONTENT_TAG).assertLeftPositionInRootIsEqualTo((-30).toDp())
            rule.onNodeWithTag(CONTENT_TAG).assertTopPositionInRootIsEqualTo((-40).toDp())
        }
    }

    @Test
    fun `scrollTo limits the position to the scroll range`() {
        val state = Scroll2DState()
        rule.setContent { OverlayContainer(state) }
        rule.waitForIdle()

        runBlocking { state.scrollTo(IntOffset(100_000, 100_000)) }
        assertEquals(state.maxValue, state.value)
        assertFalse(state.horizontalScrollableState.canScrollForward)
        assertFalse(state.verticalScrollableState.canScrollForward)

        runBlocking { state.scrollTo(IntOffset(-100_000, -100_000)) }
        assertEquals(IntOffset.Zero, state.value)
    }

    @Test
    fun `an axis view scrolls its axis only`() {
        val state = Scroll2DState()
        rule.setContent { OverlayContainer(state) }
        rule.waitForIdle()

        val consumedY = runBlocking { state.verticalScrollableState.scrollBy(50f) }
        assertEquals(50f, consumedY)
        assertEquals(IntOffset(0, 50), state.value)

        val consumedX = runBlocking { state.horizontalScrollableState.scrollBy(20f) }
        assertEquals(20f, consumedX)
        assertEquals(IntOffset(20, 50), state.value)

        assertEquals(20, state.horizontalScrollableState.scrollIndicatorState?.scrollOffset)
        assertEquals(50, state.verticalScrollableState.scrollIndicatorState?.scrollOffset)
    }

    @Test
    fun `a scroll through an axis view cancels a scroll of the 2D state`() {
        val state = Scroll2DState()
        rule.setContent { OverlayContainer(state) }
        rule.waitForIdle()

        runBlocking {
            val scrollInProgress = launch { state.scroll(MutatePriority.Default) { awaitCancellation() } }
            yield()
            assertTrue(state.isScrollInProgress)

            state.verticalScrollableState.scroll(MutatePriority.UserInput) {}
            scrollInProgress.join()

            assertTrue(scrollInProgress.isCancelled)
        }
    }

    @Test
    fun `an axis view reports the unconsumed part at the end of the range`() {
        val state = Scroll2DState()
        rule.setContent { OverlayContainer(state) }
        rule.waitForIdle()

        val consumed = runBlocking { state.verticalScrollableState.scrollBy(-25f) }

        assertEquals(0f, consumed)
        assertEquals(IntOffset.Zero, state.value)
    }

    @Test
    fun `the content starts at the right edge in a right-to-left layout`() {
        val state = Scroll2DState()
        rule.setContent {
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) { OverlayContainer(state) }
        }
        rule.waitForIdle()

        assertEquals(0, state.value.x)
        rule.onNodeWithTag(CONTENT_TAG).assertLeftPositionInRootIsEqualTo(VIEWPORT - CONTENT_WIDTH)
    }

    @Test
    fun `a drag to the right scrolls forward in a right-to-left layout`() {
        val state = Scroll2DState()
        rule.setContent {
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) { OverlayContainer(state) }
        }

        rule.onNodeWithTag(CONTAINER_TAG).performTouchInput { swipe(center, center + Offset(60f, 0f)) }
        rule.waitForIdle()

        assertTrue(state.value.x > 0, "Expected a forward horizontal scroll, but the value is ${state.value}")
    }

    @Test
    fun `a drag to the left scrolls forward in a left-to-right layout`() {
        val state = Scroll2DState()
        rule.setContent { OverlayContainer(state) }

        rule.onNodeWithTag(CONTAINER_TAG).performTouchInput { swipe(center, center - Offset(60f, 0f)) }
        rule.waitForIdle()

        assertTrue(state.value.x > 0, "Expected a forward horizontal scroll, but the value is ${state.value}")
    }

    @Test
    fun `a semantics scroll moves forward in a left-to-right layout`() {
        assertEquals(IntOffset(140, 140), semanticsScrollBy(LayoutDirection.Ltr, IntOffset(100, 100), 40f, 40f))
    }

    @Test
    fun `a semantics scroll moves forward in a right-to-left layout`() {
        assertEquals(IntOffset(140, 140), semanticsScrollBy(LayoutDirection.Rtl, IntOffset(100, 100), 40f, 40f))
    }

    @Test
    fun `performScrollTo reaches the end corner in a left-to-right layout`() {
        val state = scrollToEndCorner(LayoutDirection.Ltr)
        assertEquals(state.maxValue, state.value)
    }

    @Test
    fun `performScrollTo reaches the end corner in a right-to-left layout`() {
        val state = scrollToEndCorner(LayoutDirection.Rtl)
        assertEquals(state.maxValue, state.value)
    }

    @Test
    fun `the semantics scroll actions are absent when the user scroll is disabled`() {
        rule.setContent { OverlayContainer(Scroll2DState(), userScrollEnabled = false) }

        rule
            .onNodeWithTag(CONTENT_TAG)
            .onParent()
            .assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.VerticalScrollAxisRange))
            .assert(SemanticsMatcher.keyNotDefined(SemanticsActions.ScrollBy))
            .assert(SemanticsMatcher.keyNotDefined(SemanticsActions.ScrollByOffset))
    }

    @Test
    fun `the container leaves the mouse wheel event to the parent at the end of its range`() {
        val state = Scroll2DState()
        val parentState = ScrollState(0)
        val consumedByContainer = mutableListOf<Boolean>()
        rule.setContent {
            IntUiTheme {
                Column(Modifier.size(VIEWPORT).verticalScroll(parentState)) {
                    // In the Main pass, this node sees the event after the container and before the parent.
                    Box(Modifier.recordScrollEventConsumption(consumedByContainer)) {
                        Scrollable2DContainer(
                            modifier = Modifier.size(VIEWPORT).testTag(CONTAINER_TAG),
                            scrollState = state,
                            style = overlayStyle(),
                        ) {
                            Box(Modifier.size(CONTENT_WIDTH, CONTENT_HEIGHT))
                        }
                    }
                    Box(Modifier.size(VIEWPORT))
                }
            }
        }
        rule.waitForIdle()
        runBlocking { state.scrollTo(state.maxValue) }
        rule.waitForIdle()

        rule.onNodeWithTag(CONTAINER_TAG).performMouseInput { scroll(3f, ScrollWheel.Vertical) }
        rule.waitForIdle()

        // An unconsumed event is what a ComposePanel passes on to a Swing parent. Nested scroll cannot reach Swing.
        assertTrue(consumedByContainer.isNotEmpty(), "The wheel event did not reach the container")
        assertTrue(consumedByContainer.none { it }, "The container consumed the wheel event at the end of its range")
        assertTrue(parentState.value > 0, "Expected the parent to scroll, but its value is ${parentState.value}")
    }

    @Test
    fun `the rest of a wheel scroll at the end of the range reaches the parent through nested scroll`() {
        val state = Scroll2DState()
        val parentState = ScrollState(0)
        val leftovers = mutableListOf<Offset>()
        val observer =
            object : NestedScrollConnection {
                override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
                    if (available != Offset.Zero) leftovers += available
                    return Offset.Zero
                }
            }
        rule.setContent {
            IntUiTheme {
                Column(Modifier.size(VIEWPORT).verticalScroll(parentState)) {
                    Box(Modifier.nestedScroll(observer)) {
                        Scrollable2DContainer(
                            modifier = Modifier.size(VIEWPORT).testTag(CONTAINER_TAG),
                            scrollState = state,
                            style = overlayStyle(),
                        ) {
                            Box(Modifier.size(CONTENT_WIDTH, CONTENT_HEIGHT))
                        }
                    }
                    Box(Modifier.size(VIEWPORT))
                }
            }
        }
        rule.waitForIdle()
        // Leave less range than one wheel step, so that the container consumes the event and passes on the rest.
        runBlocking { state.scrollTo(IntOffset(0, state.maxValue.y - 2)) }
        rule.waitForIdle()

        rule.onNodeWithTag(CONTAINER_TAG).performMouseInput { scroll(1f, ScrollWheel.Vertical) }
        rule.waitForIdle()

        assertEquals(state.maxValue.y, state.value.y)
        assertTrue(leftovers.isNotEmpty(), "The container did not pass the rest of the scroll to its parents")
        assertTrue(parentState.value > 0, "Expected the parent to scroll, but its value is ${parentState.value}")
    }

    @Test
    fun `a parent that consumes the scroll in onPreScroll scrolls before the container`() {
        val state = Scroll2DState()
        var consumedByParent = Offset.Zero
        // Like a collapsing header, the parent takes all of the vertical scroll before the container gets it.
        val parent =
            object : NestedScrollConnection {
                override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                    val consumed = Offset(0f, available.y)
                    consumedByParent += consumed
                    return consumed
                }
            }
        rule.setContent {
            IntUiTheme {
                Box(Modifier.nestedScroll(parent)) {
                    Scrollable2DContainer(
                        modifier = Modifier.size(VIEWPORT).testTag(CONTAINER_TAG),
                        scrollState = state,
                        style = overlayStyle(),
                    ) {
                        Box(Modifier.size(CONTENT_WIDTH, CONTENT_HEIGHT))
                    }
                }
            }
        }
        rule.waitForIdle()

        rule.onNodeWithTag(CONTAINER_TAG).performMouseInput { scroll(3f, ScrollWheel.Vertical) }
        rule.waitForIdle()

        assertTrue(consumedByParent.y != 0f, "The parent did not get the scroll in onPreScroll")
        assertEquals(IntOffset.Zero, state.value)
    }

    @Test
    fun `the horizontal thumb starts at the right edge in a right-to-left layout`() {
        rule.setContent {
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                IntUiTheme {
                    Scrollable2DContainer(
                        modifier = Modifier.size(VIEWPORT).background(BACKGROUND).testTag(CONTAINER_TAG),
                        scrollState = Scroll2DState(),
                        style = opaqueAlwaysVisibleStyle(),
                    ) {
                        Box(Modifier.size(CONTENT_WIDTH, CONTENT_HEIGHT))
                    }
                }
            }
        }
        rule.waitForIdle()

        val image = rule.onNodeWithTag(CONTAINER_TAG).captureToImage().toPixelMap()
        val y = image.height - with(rule.density) { 7.dp.roundToPx() }
        val thumb = (0 until image.width).filter { x -> image[x, y] != BACKGROUND }
        assertTrue(thumb.isNotEmpty(), "The horizontal thumb is not drawn")
        assertTrue(
            thumb.first() > image.width / 2,
            "Expected the thumb at the right edge, but it starts at ${thumb.first()}",
        )
    }

    @Test
    fun `a drag in a container narrower than the minimum thumb length does not scroll`() {
        val state = Scroll2DState()
        rule.setContent {
            IntUiTheme {
                Scrollable2DContainer(
                    modifier = Modifier.size(40.dp, VIEWPORT).testTag(CONTAINER_TAG),
                    scrollState = state,
                    style = opaqueAlwaysVisibleStyle(),
                ) {
                    Box(Modifier.size(CONTENT_WIDTH, CONTENT_HEIGHT))
                }
            }
        }
        rule.waitForIdle()

        rule.onNodeWithTag(CONTAINER_TAG).performMouseInput {
            val start = Offset(8f, height - 7.dp.toPx())
            moveTo(start)
            press()
            moveTo(start + Offset(8f, 0f))
            release()
        }
        rule.waitForIdle()

        assertEquals(IntOffset.Zero, state.value)
    }

    @Test
    fun `the mouse wheel does not scroll when the user scroll is disabled`() {
        val state = Scroll2DState()
        rule.setContent { OverlayContainer(state, userScrollEnabled = false) }

        rule.onNodeWithTag(CONTAINER_TAG).performMouseInput { scroll(3f, ScrollWheel.Vertical) }
        rule.waitForIdle()

        assertEquals(IntOffset.Zero, state.value)
    }

    @Test
    fun `always visible scrollbars take space from the viewport on the two axes (macOS only)`() {
        if (hostOs != OS.MacOS) return

        val state = Scroll2DState()
        rule.setContent { AlwaysVisibleContainer(state) }
        rule.waitForIdle()

        val lane = with(rule.density) { ScrollbarVisibility.AlwaysVisible.default().trackThicknessExpanded.roundToPx() }
        val container = with(rule.density) { VIEWPORT.roundToPx() }
        assertEquals(IntSize(container - lane, container - lane), state.viewportSize)
    }

    @Test
    fun `always visible scrollbars stay hidden when the content fits without them (macOS only)`() {
        if (hostOs != OS.MacOS) return

        // The content fits the container, but not the viewport that is left when the two lanes take space.
        val content = VIEWPORT - 4.dp
        val state = Scroll2DState()
        rule.setContent { AlwaysVisibleContainer(state, content, content) }
        rule.waitForIdle()

        val container = with(rule.density) { VIEWPORT.roundToPx() }
        assertEquals(IntSize(container, container), state.viewportSize)
        assertEquals(IntOffset.Zero, state.maxValue)
    }

    @Test
    fun `an always visible scrollbar takes space only on the axis that needs it (macOS only)`() {
        if (hostOs != OS.MacOS) return

        // Only the width overflows. The horizontal lane leaves room for the height, so no vertical lane is necessary.
        val state = Scroll2DState()
        rule.setContent { AlwaysVisibleContainer(state, CONTENT_WIDTH, VIEWPORT - 20.dp) }
        rule.waitForIdle()

        val lane = with(rule.density) { ScrollbarVisibility.AlwaysVisible.default().trackThicknessExpanded.roundToPx() }
        val container = with(rule.density) { VIEWPORT.roundToPx() }
        assertEquals(IntSize(container, container - lane), state.viewportSize)
        assertEquals(0, state.maxValue.y)
    }

    @Test
    fun `always visible scrollbars overlay the content (Win+Linux)`() {
        if (hostOs == OS.MacOS) return

        val state = Scroll2DState()
        rule.setContent { AlwaysVisibleContainer(state) }
        rule.waitForIdle()

        val container = with(rule.density) { VIEWPORT.roundToPx() }
        assertEquals(IntSize(container, container), state.viewportSize)
    }

    @Test
    fun `jump to spot at the end of the vertical track reaches the end of long content`() {
        val state = Scroll2DState()
        rule.setContent { ScrollbarContainer(state, TrackClickBehavior.JumpToSpot, contentHeight = LONG_CONTENT) }
        rule.waitForIdle()

        // The last pixel of the track. The track leaves the corner free, so it is shorter than the viewport.
        clickAt(Offset(px(VIEWPORT - 7.dp), px(VIEWPORT - 16.dp) - 1f))

        assertEquals(state.maxValue.y, state.value.y)
    }

    @Test
    fun `a drag of the vertical thumb scrolls the vertical axis only`() {
        val state = Scroll2DState()
        rule.setContent { ScrollbarContainer(state) }
        rule.waitForIdle()
        // A press on the track would scroll by a page. A press on the thumb without a move does not scroll.
        dragFrom(onVerticalThumb(), Offset.Zero)
        assertEquals(IntOffset.Zero, state.value, "The press point is not on the vertical thumb")

        dragFrom(onVerticalThumb(), Offset(0f, 40f))

        assertEquals(0, state.value.x)
        assertTrue(state.value.y > 0, "Expected a vertical scroll, but the value is ${state.value}")
    }

    @Test
    fun `a drag of the horizontal thumb scrolls the horizontal axis only`() {
        val state = Scroll2DState()
        rule.setContent { ScrollbarContainer(state) }
        rule.waitForIdle()
        // A press on the track would scroll by a page. A press on the thumb without a move does not scroll.
        dragFrom(onHorizontalThumb(), Offset.Zero)
        assertEquals(IntOffset.Zero, state.value, "The press point is not on the horizontal thumb")

        dragFrom(onHorizontalThumb(), Offset(40f, 0f))

        assertEquals(0, state.value.y)
        assertTrue(state.value.x > 0, "Expected a horizontal scroll, but the value is ${state.value}")
    }

    @Test
    fun `a click on the vertical track scrolls the vertical axis by one page`() {
        val state = Scroll2DState()
        rule.setContent { ScrollbarContainer(state, TrackClickBehavior.NextPage) }
        rule.waitForIdle()

        clickAt(nearVerticalTrackEnd())

        assertEquals(IntOffset(0, state.viewportSize.height), state.value)
    }

    @Test
    fun `a click on the horizontal track scrolls the horizontal axis by one page`() {
        val state = Scroll2DState()
        rule.setContent { ScrollbarContainer(state, TrackClickBehavior.NextPage) }
        rule.waitForIdle()

        clickAt(nearHorizontalTrackEnd())

        assertEquals(IntOffset(state.viewportSize.width, 0), state.value)
    }

    @Test
    fun `disabled scrollbars ignore drags and clicks, and the mouse wheel still scrolls`() {
        val state = Scroll2DState()
        rule.setContent { ScrollbarContainer(state, scrollbarEnabled = false) }
        rule.waitForIdle()

        dragFrom(onVerticalThumb(), Offset(0f, 40f))
        dragFrom(onHorizontalThumb(), Offset(40f, 0f))
        clickAt(nearVerticalTrackEnd())
        clickAt(nearHorizontalTrackEnd())

        assertEquals(IntOffset.Zero, state.value)

        rule.onNodeWithTag(CONTAINER_TAG).performMouseInput {
            moveTo(Offset(px(VIEWPORT) / 2, px(VIEWPORT) / 2))
            scroll(3f, ScrollWheel.Vertical)
        }
        rule.waitForIdle()

        assertTrue(state.value.y > 0, "Expected the mouse wheel to scroll, but the value is ${state.value}")
    }

    @Test
    fun `the second overload scrolls each state with its own scrollbar`() {
        val horizontal = ScrollState(0)
        val vertical = ScrollState(0)
        rule.setContent { TwoStateContainer(horizontal, vertical) }
        rule.waitForIdle()

        dragFrom(onVerticalThumb(), Offset(0f, 40f))

        assertEquals(0, horizontal.value)
        assertTrue(vertical.value > 0, "Expected a vertical scroll, but the value is ${vertical.value}")

        val verticalValue = vertical.value
        dragFrom(onHorizontalThumb(), Offset(40f, 0f))

        assertEquals(verticalValue, vertical.value)
        assertTrue(horizontal.value > 0, "Expected a horizontal scroll, but the value is ${horizontal.value}")
    }

    @Test
    fun `the second overload scrolls through the given adapters`() {
        val horizontal = ScrollState(0)
        val vertical = ScrollState(0)
        lateinit var horizontalAdapter: CountingAdapter
        lateinit var verticalAdapter: CountingAdapter
        rule.setContent {
            val defaultHorizontalAdapter = rememberScrollbarAdapter(horizontal)
            val defaultVerticalAdapter = rememberScrollbarAdapter(vertical)
            horizontalAdapter = remember(defaultHorizontalAdapter) { CountingAdapter(defaultHorizontalAdapter) }
            verticalAdapter = remember(defaultVerticalAdapter) { CountingAdapter(defaultVerticalAdapter) }
            TwoStateContainer(horizontal, vertical, horizontalAdapter, verticalAdapter)
        }
        rule.waitForIdle()

        dragFrom(onVerticalThumb(), Offset(0f, 40f))

        assertTrue(verticalAdapter.scrollToCalls > 0, "The vertical scrollbar did not use its adapter")
        assertEquals(0, horizontalAdapter.scrollToCalls)

        dragFrom(onHorizontalThumb(), Offset(40f, 0f))

        assertTrue(horizontalAdapter.scrollToCalls > 0, "The horizontal scrollbar did not use its adapter")
        assertTrue(vertical.value > 0 && horizontal.value > 0, "Values: ${horizontal.value}, ${vertical.value}")
    }

    @Test
    fun `the container fails when the maximum height is infinite`() {
        val error =
            assertFailsWith<IllegalStateException> {
                rule.setContent {
                    IntUiTheme {
                        ForceConstraints(Constraints(maxWidth = 400, maxHeight = Constraints.Infinity)) {
                            Scrollable2DContainer { Text("content") }
                        }
                    }
                }
                rule.waitForIdle()
            }

        val message = assertNotNull(error.message)
        assertTrue(
            message.startsWith("Scrollable2DContainer was measured with an unbounded maximum height"),
            "Unexpected message: $message",
        )
    }

    @Test
    fun `the container fails when the maximum width is infinite`() {
        val error =
            assertFailsWith<IllegalStateException> {
                rule.setContent {
                    IntUiTheme {
                        ForceConstraints(Constraints(maxWidth = Constraints.Infinity, maxHeight = 400)) {
                            Scrollable2DContainer { Text("content") }
                        }
                    }
                }
                rule.waitForIdle()
            }

        val message = assertNotNull(error.message)
        assertTrue(
            message.startsWith("Scrollable2DContainer was measured with an unbounded maximum width"),
            "Unexpected message: $message",
        )
    }

    @Test
    fun `the container reports no intrinsic width`() {
        rule.setContent {
            IntUiTheme {
                Row {
                    Column(Modifier.width(IntrinsicSize.Max).height(VIEWPORT).testTag("max")) {
                        Spacer(Modifier.width(50.dp).height(1.dp))
                        Scrollable2DContainer(style = overlayStyle()) {
                            Box(Modifier.size(CONTENT_WIDTH, CONTENT_HEIGHT))
                        }
                    }
                    Column(Modifier.width(IntrinsicSize.Min).height(VIEWPORT).testTag("min")) {
                        Spacer(Modifier.width(50.dp).height(1.dp))
                        Scrollable2DContainer(style = overlayStyle()) {
                            Box(Modifier.size(CONTENT_WIDTH, CONTENT_HEIGHT))
                        }
                    }
                }
            }
        }
        rule.onNodeWithTag("max").assertWidthIsEqualTo(50.dp)
        rule.onNodeWithTag("min").assertWidthIsEqualTo(50.dp)
    }

    @Test
    fun `the container reports no intrinsic height`() {
        rule.setContent {
            IntUiTheme {
                Column {
                    Row(Modifier.height(IntrinsicSize.Max).width(VIEWPORT).testTag("max")) {
                        Spacer(Modifier.width(1.dp).height(50.dp))
                        Scrollable2DContainer(style = overlayStyle()) {
                            Box(Modifier.size(CONTENT_WIDTH, CONTENT_HEIGHT))
                        }
                    }
                    Row(Modifier.height(IntrinsicSize.Min).width(VIEWPORT).testTag("min")) {
                        Spacer(Modifier.width(1.dp).height(50.dp))
                        Scrollable2DContainer(style = overlayStyle()) {
                            Box(Modifier.size(CONTENT_WIDTH, CONTENT_HEIGHT))
                        }
                    }
                }
            }
        }
        rule.onNodeWithTag("max").assertHeightIsEqualTo(50.dp)
        rule.onNodeWithTag("min").assertHeightIsEqualTo(50.dp)
    }

    @Test
    fun `an IntrinsicSize parent does not query the intrinsics of lazy content`() {
        rule.setContent {
            IntUiTheme {
                Column(Modifier.width(IntrinsicSize.Max).height(VIEWPORT).testTag("column")) {
                    Spacer(Modifier.width(50.dp).height(1.dp))
                    val listState = rememberLazyListState()
                    Scrollable2DContainer(
                        horizontalScrollState = rememberScrollState(),
                        verticalScrollState = listState,
                        style = overlayStyle(),
                    ) {
                        LazyColumn(state = listState) { items(100) { Box(Modifier.size(20.dp)) } }
                    }
                }
            }
        }
        rule.onNodeWithTag("column").assertWidthIsEqualTo(50.dp)
    }

    private fun semanticsScrollBy(direction: LayoutDirection, start: IntOffset, x: Float, y: Float): IntOffset {
        val state = Scroll2DState(start)
        rule.setContent {
            CompositionLocalProvider(LocalLayoutDirection provides direction) { OverlayContainer(state) }
        }
        rule.waitForIdle()

        // The parent of the content is the node of Modifier.scroll2D.
        rule.onNodeWithTag(CONTENT_TAG).onParent().performSemanticsAction(SemanticsActions.ScrollBy) { it(x, y) }
        rule.waitForIdle()
        return state.value
    }

    private fun scrollToEndCorner(direction: LayoutDirection): Scroll2DState {
        val state = Scroll2DState()
        rule.setContent {
            CompositionLocalProvider(LocalLayoutDirection provides direction) {
                IntUiTheme {
                    Scrollable2DContainer(
                        modifier = Modifier.size(VIEWPORT),
                        scrollState = state,
                        style = overlayStyle(),
                    ) {
                        Box(Modifier.size(CONTENT_WIDTH, CONTENT_HEIGHT)) {
                            Box(Modifier.align(Alignment.BottomEnd).size(20.dp).testTag(END_CORNER_TAG))
                        }
                    }
                }
            }
        }

        rule.onNodeWithTag(END_CORNER_TAG).performScrollTo()
        rule.waitForIdle()
        return state
    }

    @Composable
    private fun OverlayContainer(
        state: Scroll2DState,
        contentWidth: Dp = CONTENT_WIDTH,
        contentHeight: Dp = CONTENT_HEIGHT,
        userScrollEnabled: Boolean = true,
    ) {
        IntUiTheme {
            Scrollable2DContainer(
                modifier = Modifier.size(VIEWPORT).testTag(CONTAINER_TAG),
                scrollState = state,
                style = overlayStyle(),
                userScrollEnabled = userScrollEnabled,
            ) {
                Box(Modifier.size(contentWidth, contentHeight).testTag(CONTENT_TAG))
            }
        }
    }

    @Composable
    private fun AlwaysVisibleContainer(
        state: Scroll2DState,
        contentWidth: Dp = CONTENT_WIDTH,
        contentHeight: Dp = CONTENT_HEIGHT,
    ) {
        IntUiTheme {
            Scrollable2DContainer(
                modifier = Modifier.size(VIEWPORT).testTag(CONTAINER_TAG),
                scrollState = state,
                style = styleWith(ScrollbarVisibility.AlwaysVisible.default()),
            ) {
                Box(Modifier.size(contentWidth, contentHeight).testTag(CONTENT_TAG))
            }
        }
    }

    // A scrollbar of this style never takes space from the content, on any OS.
    @Composable private fun overlayStyle(): ScrollbarStyle = styleWith(ScrollbarVisibility.WhenScrolling.default())

    // The thumbs of this style are always drawn, and the lanes have no background, so only the thumbs have color.
    @Composable
    private fun opaqueAlwaysVisibleStyle(
        clickBehavior: TrackClickBehavior = JewelTheme.scrollbarStyle.trackClickBehavior
    ): ScrollbarStyle =
        styleWith(
            ScrollbarVisibility.AlwaysVisible(
                trackThickness = 14.dp,
                trackPadding = PaddingValues(2.dp),
                trackPaddingWithBorder = PaddingValues(2.dp),
                thumbColorAnimationDuration = Duration.ZERO,
                trackColorAnimationDuration = Duration.ZERO,
                scrollbarBackgroundColorLight = Color.Transparent,
                scrollbarBackgroundColorDark = Color.Transparent,
            ),
            clickBehavior,
        )

    @Composable
    private fun ScrollbarContainer(
        state: Scroll2DState,
        clickBehavior: TrackClickBehavior = TrackClickBehavior.NextPage,
        scrollbarEnabled: Boolean = true,
        contentHeight: Dp = CONTENT_HEIGHT,
    ) {
        IntUiTheme {
            Scrollable2DContainer(
                modifier = Modifier.size(VIEWPORT).testTag(CONTAINER_TAG),
                scrollState = state,
                style = opaqueAlwaysVisibleStyle(clickBehavior),
                scrollbarEnabled = scrollbarEnabled,
            ) {
                Box(Modifier.size(CONTENT_WIDTH, contentHeight).testTag(CONTENT_TAG))
            }
        }
    }

    /** The overload that does not own the scroll, with content that scrolls through two one-axis states. */
    @Composable
    private fun TwoStateContainer(
        horizontal: ScrollState,
        vertical: ScrollState,
        horizontalAdapter: ScrollbarAdapter? = null,
        verticalAdapter: ScrollbarAdapter? = null,
    ) {
        IntUiTheme {
            Scrollable2DContainer(
                horizontalScrollState = horizontal,
                verticalScrollState = vertical,
                modifier = Modifier.size(VIEWPORT).testTag(CONTAINER_TAG),
                style = opaqueAlwaysVisibleStyle(),
                horizontalAdapter = horizontalAdapter,
                verticalAdapter = verticalAdapter,
            ) {
                Box(Modifier.verticalScroll(vertical).horizontalScroll(horizontal)) {
                    Box(Modifier.size(CONTENT_WIDTH, CONTENT_HEIGHT))
                }
            }
        }
    }

    // Points on the scrollbars of a container of the VIEWPORT size. Each lane is 14 dp thick, each track has 2 dp of
    // padding, and the corner takes 14 dp from the end of each track. At the start of the range, each thumb is at the
    // start of its track.
    private fun onVerticalThumb() = Offset(px(VIEWPORT - 7.dp), px(2.dp) + 8f)

    private fun onHorizontalThumb() = Offset(px(2.dp) + 8f, px(VIEWPORT - 7.dp))

    private fun nearVerticalTrackEnd() = Offset(px(VIEWPORT - 7.dp), px(VIEWPORT - 16.dp) - 6f)

    private fun nearHorizontalTrackEnd() = Offset(px(VIEWPORT - 16.dp) - 6f, px(VIEWPORT - 7.dp))

    private fun px(value: Dp): Float = with(rule.density) { value.toPx() }

    private fun dragFrom(start: Offset, delta: Offset) {
        rule.onNodeWithTag(CONTAINER_TAG).performMouseInput {
            moveTo(start)
            press()
            moveTo(start + delta)
            release()
        }
        rule.waitForIdle()
    }

    private fun clickAt(position: Offset) {
        rule.onNodeWithTag(CONTAINER_TAG).performMouseInput { click(position) }
        rule.waitForIdle()
    }

    /** Passes every call on to [delegate], and counts the calls of [scrollTo]. */
    private class CountingAdapter(private val delegate: ScrollbarAdapter) : ScrollbarAdapter by delegate {
        var scrollToCalls = 0
            private set

        override suspend fun scrollTo(scrollOffset: Double) {
            scrollToCalls++
            delegate.scrollTo(scrollOffset)
        }
    }

    private fun Modifier.recordScrollEventConsumption(consumed: MutableList<Boolean>): Modifier =
        pointerInput(Unit) {
            awaitPointerEventScope {
                while (true) {
                    val event = awaitPointerEvent(PointerEventPass.Main)
                    if (event.type == PointerEventType.Scroll) consumed += event.changes.first().isConsumed
                }
            }
        }

    @Composable
    private fun styleWith(
        visibility: ScrollbarVisibility,
        clickBehavior: TrackClickBehavior = JewelTheme.scrollbarStyle.trackClickBehavior,
    ): ScrollbarStyle =
        ScrollbarStyle(
            colors = JewelTheme.scrollbarStyle.colors,
            metrics = JewelTheme.scrollbarStyle.metrics,
            trackClickBehavior = clickBehavior,
            scrollbarVisibility = visibility,
        )

    @Composable
    private fun ForceConstraints(constraints: Constraints, content: @Composable () -> Unit) {
        Layout(content) { measurables, _ ->
            val placeable = measurables.first().measure(constraints)
            layout(placeable.width, placeable.height) { placeable.place(0, 0) }
        }
    }

    private companion object {
        const val CONTAINER_TAG = "container"
        const val CONTENT_TAG = "content"
        const val END_CORNER_TAG = "end corner"
        val VIEWPORT = 200.dp
        val CONTENT_WIDTH = 600.dp
        val CONTENT_HEIGHT = 500.dp
        // Long enough that a jump that scales by the viewport instead of the track stops short of the end.
        val LONG_CONTENT = 15_000.dp
        val BACKGROUND = Color.White
    }
}
