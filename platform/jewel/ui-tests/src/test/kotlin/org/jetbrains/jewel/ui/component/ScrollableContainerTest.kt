// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jewel.ui.component

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertHeightIsEqualTo
import androidx.compose.ui.test.assertWidthIsEqualTo
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.jetbrains.jewel.IntUiTestTheme
import org.jetbrains.jewel.foundation.Stroke
import org.jetbrains.jewel.foundation.modifier.border
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.intui.standalone.styling.default
import org.jetbrains.jewel.ui.component.styling.ScrollbarStyle
import org.jetbrains.jewel.ui.component.styling.ScrollbarVisibility
import org.jetbrains.jewel.ui.component.styling.TrackClickBehavior
import org.jetbrains.jewel.ui.theme.scrollbarStyle
import org.jetbrains.skiko.OS
import org.jetbrains.skiko.hostOs
import org.junit.Rule
import org.junit.Test

@Suppress("LargeClass") // Big test suite is big
class ScrollableContainerTest {
    @get:Rule val rule = createComposeRule()

    @Test
    fun `content should be as wide as container when scrollbar is always visible and content is too short to scroll (non-lazy)`() {
        val scrollState = ScrollState(0)
        rule.setContent {
            IntUiTestTheme {
                val scrollbarStyle by rememberScrollbarStyle(alwaysVisible = true)

                VerticallyScrollableContainer(
                    modifier = Modifier.size(width = 200.dp, height = 300.dp).testTag("container"),
                    scrollState = scrollState,
                    style = scrollbarStyle,
                ) {
                    Column(modifier = Modifier.testTag("column")) {
                        repeat(3) {
                            Text("Item $it", modifier = Modifier.fillMaxWidth().testTag("item-$it").padding(20.dp))
                        }
                    }
                }
            }
        }

        assertFalse(scrollState.canScrollForward)

        rule.onNodeWithTag("container").assertWidthIsEqualTo(200.dp)
        rule.onNodeWithTag("column").assertWidthIsEqualTo(200.dp)
        rule.onNodeWithTag("item-0").assertWidthIsEqualTo(200.dp)
    }

    @Test
    fun `content should leave room for vScrollbar when scrollbar is always visible and content can scroll (non-lazy) (macOS only)`() {
        if (hostOs != OS.MacOS) return

        val scrollState = ScrollState(0)
        rule.setContent {
            IntUiTestTheme {
                val scrollbarStyle by rememberScrollbarStyle(alwaysVisible = true)

                VerticallyScrollableContainer(
                    modifier = Modifier.size(width = 200.dp, height = 300.dp).testTag("container"),
                    scrollState = scrollState,
                    style = scrollbarStyle,
                ) {
                    Column(modifier = Modifier.testTag("column")) {
                        repeat(50) {
                            Text("Item $it", modifier = Modifier.fillMaxWidth().testTag("item-$it").padding(20.dp))
                        }
                    }
                }
            }
        }

        assertTrue(scrollState.canScrollForward)

        rule.onNodeWithTag("container").assertWidthIsEqualTo(200.dp)

        val scrollbarWidth = ScrollbarVisibility.AlwaysVisible.default().trackThicknessExpanded
        rule.onNodeWithTag("column").assertWidthIsEqualTo(200.dp - scrollbarWidth)
        rule.onNodeWithTag("item-0").assertWidthIsEqualTo(200.dp - scrollbarWidth)
    }

    @Test
    fun `content should not leave room for vScrollbar when scrollbar is always visible and content can scroll (non-lazy) (Win+Linux)`() {
        if (hostOs == OS.MacOS) return

        val scrollState = ScrollState(0)
        rule.setContent {
            IntUiTestTheme {
                val scrollbarStyle by rememberScrollbarStyle(alwaysVisible = true)

                VerticallyScrollableContainer(
                    modifier = Modifier.size(width = 200.dp, height = 300.dp).testTag("container"),
                    scrollState = scrollState,
                    style = scrollbarStyle,
                ) {
                    Column(modifier = Modifier.testTag("column")) {
                        repeat(50) {
                            Text("Item $it", modifier = Modifier.fillMaxWidth().testTag("item-$it").padding(20.dp))
                        }
                    }
                }
            }
        }

        assertTrue(scrollState.canScrollForward)

        rule.onNodeWithTag("container").assertWidthIsEqualTo(200.dp)
        rule.onNodeWithTag("column").assertWidthIsEqualTo(200.dp)
        rule.onNodeWithTag("item-0").assertWidthIsEqualTo(200.dp)
    }

    @Test
    fun `content should be as wide as container when scrollbar is always visible and content is too short to scroll (lazy)`() {
        val scrollState = LazyListState()
        rule.setContent {
            IntUiTestTheme {
                val scrollbarStyle by rememberScrollbarStyle(alwaysVisible = true)

                VerticallyScrollableContainer(
                    modifier = Modifier.size(width = 200.dp, height = 300.dp).testTag("container"),
                    scrollState = scrollState,
                    style = scrollbarStyle,
                ) {
                    LazyColumn(modifier = Modifier.testTag("lazyColumn"), state = scrollState) {
                        items(3) {
                            Text("Item $it", modifier = Modifier.fillMaxWidth().testTag("item-$it").padding(20.dp))
                        }
                    }
                }
            }
        }

        assertFalse(scrollState.canScrollForward)

        rule.onNodeWithTag("container").assertWidthIsEqualTo(200.dp)
        rule.onNodeWithTag("lazyColumn").assertWidthIsEqualTo(200.dp)
        rule.onNodeWithTag("item-0").assertWidthIsEqualTo(200.dp)
    }

    @Test
    fun `content should leave room for vScrollbar when scrollbar is always visible and content can scroll (lazy) (macOS only)`() {
        if (hostOs != OS.MacOS) return

        val scrollState = LazyListState()
        rule.setContent {
            IntUiTestTheme {
                val scrollbarStyle by rememberScrollbarStyle(alwaysVisible = true)

                VerticallyScrollableContainer(
                    modifier = Modifier.size(width = 200.dp, height = 300.dp).testTag("container"),
                    scrollState = scrollState,
                    style = scrollbarStyle,
                ) {
                    LazyColumn(modifier = Modifier.testTag("lazyColumn"), state = scrollState) {
                        items(50) {
                            Text("Item $it", modifier = Modifier.fillMaxWidth().testTag("item-$it").padding(20.dp))
                        }
                    }
                }
            }
        }

        assertTrue(scrollState.canScrollForward)

        rule.onNodeWithTag("container").assertWidthIsEqualTo(200.dp)

        val scrollbarWidth = ScrollbarVisibility.AlwaysVisible.default().trackThicknessExpanded
        rule.onNodeWithTag("lazyColumn").assertWidthIsEqualTo(200.dp - scrollbarWidth)
        rule.onNodeWithTag("item-0").assertWidthIsEqualTo(200.dp - scrollbarWidth)
    }

    @Test
    fun `content should not leave room for vScrollbar when scrollbar is always visible and content can scroll (lazy) (Win+Linux)`() {
        if (hostOs == OS.MacOS) return

        val scrollState = LazyListState()
        rule.setContent {
            IntUiTestTheme {
                val scrollbarStyle by rememberScrollbarStyle(alwaysVisible = true)

                VerticallyScrollableContainer(
                    modifier = Modifier.size(width = 200.dp, height = 300.dp).testTag("container"),
                    scrollState = scrollState,
                    style = scrollbarStyle,
                ) {
                    LazyColumn(modifier = Modifier.testTag("lazyColumn"), state = scrollState) {
                        items(50) {
                            Text("Item $it", modifier = Modifier.fillMaxWidth().testTag("item-$it").padding(20.dp))
                        }
                    }
                }
            }
        }

        assertTrue(scrollState.canScrollForward)

        rule.onNodeWithTag("container").assertWidthIsEqualTo(200.dp)
        rule.onNodeWithTag("lazyColumn").assertWidthIsEqualTo(200.dp)
        rule.onNodeWithTag("item-0").assertWidthIsEqualTo(200.dp)
    }

    @Test
    fun `content should be as tall as container when scrollbar is always visible and content is too narrow to scroll (non-lazy)`() {
        val scrollState = ScrollState(0)
        rule.setContent {
            IntUiTestTheme {
                val scrollbarStyle by rememberScrollbarStyle(alwaysVisible = true)

                HorizontallyScrollableContainer(
                    modifier = Modifier.width(300.dp).testTag("container"),
                    scrollState = scrollState,
                    style = scrollbarStyle,
                ) {
                    Text("Banana", modifier = Modifier.height(20.dp).testTag("text"), maxLines = 1)
                }
            }
        }

        assertFalse(scrollState.canScrollForward)

        rule.onNodeWithTag("container").assertHeightIsEqualTo(20.dp)
        rule.onNodeWithTag("text").assertHeightIsEqualTo(20.dp)
    }

    @Test
    fun `content should leave room for hScrollbar when scrollbar is always visible and content can scroll (non-lazy) (macOS only)`() {
        if (hostOs != OS.MacOS) return

        val scrollState = ScrollState(0)
        rule.setContent {
            IntUiTestTheme {
                val scrollbarStyle by rememberScrollbarStyle(alwaysVisible = true)

                HorizontallyScrollableContainer(
                    modifier = Modifier.width(300.dp).testTag("container"),
                    scrollState = scrollState,
                    style = scrollbarStyle,
                ) {
                    Text("banana".repeat(100), modifier = Modifier.height(20.dp).testTag("text"), maxLines = 1)
                }
            }
        }

        assertTrue(scrollState.canScrollForward)

        val scrollbarHeight = ScrollbarVisibility.AlwaysVisible.default().trackThicknessExpanded
        rule.onNodeWithTag("container").assertHeightIsEqualTo(20.dp + scrollbarHeight)
        rule.onNodeWithTag("text").assertHeightIsEqualTo(20.dp)
    }

    @Test
    fun `content should not leave room for hScrollbar when scrollbar is always visible and content can scroll (non-lazy) (Win+Linux)`() {
        if (hostOs == OS.MacOS) return

        val scrollState = ScrollState(0)
        rule.setContent {
            IntUiTestTheme {
                val scrollbarStyle by rememberScrollbarStyle(alwaysVisible = true)

                HorizontallyScrollableContainer(
                    modifier = Modifier.width(300.dp).testTag("container"),
                    scrollState = scrollState,
                    style = scrollbarStyle,
                ) {
                    Text("banana".repeat(100), modifier = Modifier.height(20.dp).testTag("text"), maxLines = 1)
                }
            }
        }

        assertTrue(scrollState.canScrollForward)

        rule.onNodeWithTag("container").assertHeightIsEqualTo(20.dp)
        rule.onNodeWithTag("text").assertHeightIsEqualTo(20.dp)
    }

    @Test
    fun `content should be as tall as container when scrollbar is always visible and content is too narrow to scroll (lazy)`() {
        val scrollState = LazyListState()
        val words = loremIpsum.split(' ').filter { it.isNotBlank() }.take(3)

        rule.setContent {
            IntUiTestTheme {
                val scrollbarStyle by rememberScrollbarStyle(alwaysVisible = true)

                HorizontallyScrollableContainer(
                    scrollState = scrollState,
                    modifier =
                        Modifier.fillMaxWidth()
                            .border(Stroke.Alignment.Outside, 1.dp, JewelTheme.globalColors.borders.normal)
                            .testTag("container"),
                    style = scrollbarStyle,
                ) {
                    LazyRow(
                        state = scrollState,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier.testTag("lazyRow"),
                    ) {
                        itemsIndexed(words) { i, word ->
                            Text(word, Modifier.height(20.dp).padding(horizontal = 8.dp).testTag("item-$i"))
                        }
                    }
                }
            }
        }

        assertFalse(scrollState.canScrollForward)

        rule.onNodeWithTag("container").assertHeightIsEqualTo(20.dp)
        rule.onNodeWithTag("lazyRow").assertHeightIsEqualTo(20.dp)
        rule.onNodeWithTag("item-0").assertHeightIsEqualTo(20.dp)
    }

    @Test
    fun `content should leave room for hScrollbar when scrollbar is always visible and content can scroll (lazy) (macOS only)`() {
        if (hostOs != OS.MacOS) return

        val scrollState = LazyListState()
        val words = loremIpsum.split(' ').filter { it.isNotBlank() }

        rule.setContent {
            IntUiTestTheme {
                val scrollbarStyle by rememberScrollbarStyle(alwaysVisible = true)

                HorizontallyScrollableContainer(
                    scrollState = scrollState,
                    modifier =
                        Modifier.fillMaxWidth()
                            .border(Stroke.Alignment.Outside, 1.dp, JewelTheme.globalColors.borders.normal)
                            .testTag("container"),
                    style = scrollbarStyle,
                ) {
                    LazyRow(
                        state = scrollState,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier.testTag("lazyRow"),
                    ) {
                        itemsIndexed(words) { i, word ->
                            Text(word, Modifier.height(20.dp).padding(horizontal = 8.dp).testTag("item-$i"))
                        }
                    }
                }
            }
        }

        assertTrue(scrollState.canScrollForward)

        val scrollbarHeight = ScrollbarVisibility.AlwaysVisible.default().trackThicknessExpanded
        rule.onNodeWithTag("container").assertHeightIsEqualTo(20.dp + scrollbarHeight)
        rule.onNodeWithTag("lazyRow").assertHeightIsEqualTo(20.dp)
        rule.onNodeWithTag("item-0").assertHeightIsEqualTo(20.dp)
    }

    @Test
    fun `content should not leave room for hScrollbar when scrollbar is always visible and content can scroll (lazy) (Win+Linux)`() {
        if (hostOs == OS.MacOS) return

        val scrollState = LazyListState()
        val words = loremIpsum.split(' ').filter { it.isNotBlank() }

        rule.setContent {
            IntUiTestTheme {
                val scrollbarStyle by rememberScrollbarStyle(alwaysVisible = true)

                HorizontallyScrollableContainer(
                    scrollState = scrollState,
                    modifier =
                        Modifier.fillMaxWidth()
                            .border(Stroke.Alignment.Outside, 1.dp, JewelTheme.globalColors.borders.normal)
                            .testTag("container"),
                    style = scrollbarStyle,
                ) {
                    LazyRow(
                        state = scrollState,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier.testTag("lazyRow"),
                    ) {
                        itemsIndexed(words) { i, word ->
                            Text(word, Modifier.height(20.dp).padding(horizontal = 8.dp).testTag("item-$i"))
                        }
                    }
                }
            }
        }

        assertTrue(scrollState.canScrollForward)

        rule.onNodeWithTag("container").assertHeightIsEqualTo(20.dp)
        rule.onNodeWithTag("lazyRow").assertHeightIsEqualTo(20.dp)
        rule.onNodeWithTag("item-0").assertHeightIsEqualTo(20.dp)
    }

    @Test
    fun `vertical container should make room for the scrollbar when is AlwaysVisible and width is not constrained (macOS only)`() {
        if (hostOs != OS.MacOS) return

        val scrollState = ScrollState(0)
        val count = mutableIntStateOf(3)
        rule.setContent {
            IntUiTestTheme {
                val scrollbarStyle by rememberScrollbarStyle(alwaysVisible = true)

                VerticallyScrollableContainer(
                    modifier = Modifier.testTag("container").height(200.dp),
                    scrollState = scrollState,
                    style = scrollbarStyle,
                ) {
                    Column(modifier = Modifier.testTag("column")) {
                        repeat(count.value) {
                            Text("Item $it", modifier = Modifier.size(50.dp, 20.dp).testTag("item-$it"))
                        }
                    }
                }
            }
        }

        // Check when not scrollable first
        assertFalse(scrollState.canScrollForward)

        rule.onNodeWithTag("container").assertWidthIsEqualTo(50.dp)
        rule.onNodeWithTag("column").assertWidthIsEqualTo(50.dp)
        rule.onNodeWithTag("item-0").assertWidthIsEqualTo(50.dp)

        // Then add items to ensure it's scrollable and check again
        count.value = 100
        rule.mainClock.advanceTimeByFrame()
        assertTrue(scrollState.canScrollForward)

        val scrollbarWidth = ScrollbarVisibility.AlwaysVisible.default().trackThicknessExpanded
        rule.onNodeWithTag("container").assertWidthIsEqualTo(50.dp + scrollbarWidth)
        rule.onNodeWithTag("column").assertWidthIsEqualTo(50.dp)
        rule.onNodeWithTag("item-0").assertWidthIsEqualTo(50.dp)
    }

    @Test
    fun `vertical container should not make room for the scrollbar when is AlwaysVisible and width is not constrained (Win+Linux)`() {
        if (hostOs == OS.MacOS) return

        val scrollState = ScrollState(0)
        val count = mutableIntStateOf(3)
        rule.setContent {
            IntUiTestTheme {
                val scrollbarStyle by rememberScrollbarStyle(alwaysVisible = true)

                VerticallyScrollableContainer(
                    modifier = Modifier.testTag("container").height(200.dp),
                    scrollState = scrollState,
                    style = scrollbarStyle,
                ) {
                    Column(modifier = Modifier.testTag("column")) {
                        repeat(count.value) {
                            Text("Item $it", modifier = Modifier.size(50.dp, 20.dp).testTag("item-$it"))
                        }
                    }
                }
            }
        }

        // Check when not scrollable first
        assertFalse(scrollState.canScrollForward)

        rule.onNodeWithTag("container").assertWidthIsEqualTo(50.dp)
        rule.onNodeWithTag("column").assertWidthIsEqualTo(50.dp)
        rule.onNodeWithTag("item-0").assertWidthIsEqualTo(50.dp)

        // Then add items to ensure it's scrollable and check again
        count.value = 100
        rule.mainClock.advanceTimeByFrame()
        assertTrue(scrollState.canScrollForward)

        rule.onNodeWithTag("container").assertWidthIsEqualTo(50.dp)
        rule.onNodeWithTag("column").assertWidthIsEqualTo(50.dp)
        rule.onNodeWithTag("item-0").assertWidthIsEqualTo(50.dp)
    }

    @Test
    fun `vertical container should not make room for the scrollbar when is WhenScrolling and width is not constrained`() {
        val scrollState = ScrollState(0)
        val count = mutableIntStateOf(3)
        rule.setContent {
            IntUiTestTheme {
                val scrollbarStyle by rememberScrollbarStyle(alwaysVisible = false)

                VerticallyScrollableContainer(
                    modifier = Modifier.testTag("container").height(200.dp),
                    scrollState = scrollState,
                    style = scrollbarStyle,
                ) {
                    Column(modifier = Modifier.testTag("column")) {
                        repeat(count.value) {
                            Text("Item $it", modifier = Modifier.size(50.dp, 20.dp).testTag("item-$it"))
                        }
                    }
                }
            }
        }

        // Check when not scrollable first
        assertFalse(scrollState.canScrollForward)

        rule.onNodeWithTag("container").assertWidthIsEqualTo(50.dp)
        rule.onNodeWithTag("column").assertWidthIsEqualTo(50.dp)
        rule.onNodeWithTag("item-0").assertWidthIsEqualTo(50.dp)

        // Then add items to ensure it's scrollable and check again
        count.value = 100
        rule.mainClock.advanceTimeByFrame()
        assertTrue(scrollState.canScrollForward)

        rule.onNodeWithTag("container").assertWidthIsEqualTo(50.dp)
        rule.onNodeWithTag("column").assertWidthIsEqualTo(50.dp)
        rule.onNodeWithTag("item-0").assertWidthIsEqualTo(50.dp)
    }

    @Test
    fun `horizontal container should make room for the scrollbar when is AlwaysVisible and height is not constrained (macOS only)`() {
        if (hostOs != OS.MacOS) return

        val scrollState = ScrollState(0)
        val count = mutableIntStateOf(3)
        rule.setContent {
            IntUiTestTheme {
                val scrollbarStyle by rememberScrollbarStyle(alwaysVisible = true)

                HorizontallyScrollableContainer(
                    modifier = Modifier.testTag("container").width(200.dp),
                    scrollState = scrollState,
                    style = scrollbarStyle,
                ) {
                    Row(modifier = Modifier.testTag("column")) {
                        repeat(count.value) {
                            Text("Item $it", modifier = Modifier.size(50.dp, 20.dp).testTag("item-$it"))
                        }
                    }
                }
            }
        }

        // Check when not scrollable first
        assertFalse(scrollState.canScrollForward)

        rule.onNodeWithTag("container").assertHeightIsEqualTo(20.dp)
        rule.onNodeWithTag("column").assertHeightIsEqualTo(20.dp)
        rule.onNodeWithTag("item-0").assertHeightIsEqualTo(20.dp)

        // Then add items to ensure it's scrollable and check again
        count.value = 100
        rule.mainClock.advanceTimeByFrame()
        assertTrue(scrollState.canScrollForward)

        val scrollbarHeight = ScrollbarVisibility.AlwaysVisible.default().trackThicknessExpanded
        rule.onNodeWithTag("container").assertHeightIsEqualTo(20.dp + scrollbarHeight)
        rule.onNodeWithTag("column").assertHeightIsEqualTo(20.dp)
        rule.onNodeWithTag("item-0").assertHeightIsEqualTo(20.dp)
    }

    @Test
    fun `horizontal container should not make room for the scrollbar when is AlwaysVisible and height is not constrained (Win+Linux)`() {
        if (hostOs == OS.MacOS) return

        val scrollState = ScrollState(0)
        val count = mutableIntStateOf(3)
        rule.setContent {
            IntUiTestTheme {
                val scrollbarStyle by rememberScrollbarStyle(alwaysVisible = true)

                HorizontallyScrollableContainer(
                    modifier = Modifier.testTag("container").width(200.dp),
                    scrollState = scrollState,
                    style = scrollbarStyle,
                ) {
                    Row(modifier = Modifier.testTag("column")) {
                        repeat(count.value) {
                            Text("Item $it", modifier = Modifier.size(50.dp, 20.dp).testTag("item-$it"))
                        }
                    }
                }
            }
        }

        // Check when not scrollable first
        assertFalse(scrollState.canScrollForward)

        rule.onNodeWithTag("container").assertHeightIsEqualTo(20.dp)
        rule.onNodeWithTag("column").assertHeightIsEqualTo(20.dp)
        rule.onNodeWithTag("item-0").assertHeightIsEqualTo(20.dp)

        // Then add items to ensure it's scrollable and check again
        count.value = 100
        rule.mainClock.advanceTimeByFrame()
        assertTrue(scrollState.canScrollForward)

        rule.onNodeWithTag("container").assertHeightIsEqualTo(20.dp)
        rule.onNodeWithTag("column").assertHeightIsEqualTo(20.dp)
        rule.onNodeWithTag("item-0").assertHeightIsEqualTo(20.dp)
    }

    @Test
    fun `horizontal container should not make room for the scrollbar when is WhenScrolling and height is not constrained`() {
        val scrollState = ScrollState(0)
        val count = mutableIntStateOf(3)
        rule.setContent {
            IntUiTestTheme {
                val scrollbarStyle by rememberScrollbarStyle(alwaysVisible = false)

                HorizontallyScrollableContainer(
                    modifier = Modifier.testTag("container").width(200.dp),
                    scrollState = scrollState,
                    style = scrollbarStyle,
                ) {
                    Row(modifier = Modifier.testTag("column")) {
                        repeat(count.value) {
                            Text("Item $it", modifier = Modifier.size(50.dp, 20.dp).testTag("item-$it"))
                        }
                    }
                }
            }
        }

        // Check when not scrollable first
        assertFalse(scrollState.canScrollForward)

        rule.onNodeWithTag("container").assertHeightIsEqualTo(20.dp)
        rule.onNodeWithTag("column").assertHeightIsEqualTo(20.dp)
        rule.onNodeWithTag("item-0").assertHeightIsEqualTo(20.dp)

        // Then add items to ensure it's scrollable and check again
        count.value = 100
        rule.mainClock.advanceTimeByFrame()
        assertTrue(scrollState.canScrollForward)

        rule.onNodeWithTag("container").assertHeightIsEqualTo(20.dp)
        rule.onNodeWithTag("column").assertHeightIsEqualTo(20.dp)
        rule.onNodeWithTag("item-0").assertHeightIsEqualTo(20.dp)
    }

    @Test
    fun `non-scrolling vertical container should respect width constraints when is AlwaysVisible`() {
        val scrollState = ScrollState(0)
        val itemWidth = mutableIntStateOf(20)
        rule.setContent {
            IntUiTestTheme {
                val scrollbarStyle by rememberScrollbarStyle(alwaysVisible = true)

                VerticallyScrollableContainer(
                    modifier = Modifier.testTag("container").widthIn(min = 30.dp, max = 50.dp).height(200.dp),
                    scrollState = scrollState,
                    style = scrollbarStyle,
                ) {
                    Column(modifier = Modifier.testTag("column")) {
                        repeat(3) {
                            Text(
                                "Item $it",
                                modifier = Modifier.size(itemWidth.value.dp, 20.dp).testTag("item-$it"),
                                maxLines = 1,
                            )
                        }
                    }
                }
            }
        }

        assertFalse(scrollState.canScrollForward)

        // Start with an item narrower than the minWidth of 30.dp
        rule.onNodeWithTag("container").assertWidthIsEqualTo(30.dp)
        rule.onNodeWithTag("column").assertWidthIsEqualTo(20.dp)
        rule.onNodeWithTag("item-0").assertWidthIsEqualTo(20.dp)

        // Then make the item exactly as wide as the minWidth of 30.dp
        itemWidth.value = 30
        rule.mainClock.advanceTimeByFrame()
        assertFalse(scrollState.canScrollForward)

        rule.onNodeWithTag("container").assertWidthIsEqualTo(30.dp)
        rule.onNodeWithTag("column").assertWidthIsEqualTo(30.dp)
        rule.onNodeWithTag("item-0").assertWidthIsEqualTo(30.dp)

        // Then make the item exactly as wide as the maxWidth of 50.dp
        itemWidth.value = 50
        rule.mainClock.advanceTimeByFrame()
        assertFalse(scrollState.canScrollForward)

        rule.onNodeWithTag("container").assertWidthIsEqualTo(50.dp)
        rule.onNodeWithTag("column").assertWidthIsEqualTo(50.dp)
        rule.onNodeWithTag("item-0").assertWidthIsEqualTo(50.dp)

        // Lastly, make the item wider than the maxWidth of 50.dp
        itemWidth.value = 60
        rule.mainClock.advanceTimeByFrame()
        assertFalse(scrollState.canScrollForward)

        rule.onNodeWithTag("container").assertWidthIsEqualTo(50.dp)
        rule.onNodeWithTag("column").assertWidthIsEqualTo(50.dp)
        rule.onNodeWithTag("item-0").assertWidthIsEqualTo(50.dp)
    }

    @Test
    fun `scrolling vertical container should respect width constraints when is AlwaysVisible (macOS only)`() {
        if (hostOs != OS.MacOS) return

        val scrollState = ScrollState(0)
        val itemWidth = mutableIntStateOf(20)
        rule.setContent {
            IntUiTestTheme {
                val scrollbarStyle by rememberScrollbarStyle(alwaysVisible = true)

                VerticallyScrollableContainer(
                    modifier = Modifier.testTag("container").widthIn(min = 50.dp, max = 150.dp).height(200.dp),
                    scrollState = scrollState,
                    style = scrollbarStyle,
                ) {
                    Column(modifier = Modifier.testTag("column")) {
                        repeat(100) {
                            Text(
                                "Item $it",
                                modifier = Modifier.size(itemWidth.value.dp, 20.dp).testTag("item-$it"),
                                maxLines = 1,
                            )
                        }
                    }
                }
            }
        }

        assertTrue(scrollState.canScrollForward)

        // Start with an item narrower than the minWidth of 50.dp
        rule.onNodeWithTag("container").assertWidthIsEqualTo(50.dp)
        rule.onNodeWithTag("column").assertWidthIsEqualTo(20.dp)
        rule.onNodeWithTag("item-0").assertWidthIsEqualTo(20.dp)

        // Then make the item exactly as wide as the minWidth of 50.dp
        itemWidth.value = 50
        rule.mainClock.advanceTimeByFrame()
        assertTrue(scrollState.canScrollForward)

        val scrollbarWidth = ScrollbarVisibility.AlwaysVisible.default().trackThicknessExpanded
        rule.onNodeWithTag("container").assertWidthIsEqualTo(50.dp + scrollbarWidth)
        rule.onNodeWithTag("column").assertWidthIsEqualTo(50.dp)
        rule.onNodeWithTag("item-0").assertWidthIsEqualTo(50.dp)

        // Then make the item exactly as wide as the maxWidth of 150.dp
        itemWidth.value = 150
        rule.mainClock.advanceTimeByFrame()
        assertTrue(scrollState.canScrollForward)

        rule.onNodeWithTag("container").assertWidthIsEqualTo(150.dp)
        rule.onNodeWithTag("column").assertWidthIsEqualTo(150.dp - scrollbarWidth)
        rule.onNodeWithTag("item-0").assertWidthIsEqualTo(150.dp - scrollbarWidth)

        // Lastly, make the item wider than the maxWidth of 150.dp
        itemWidth.value = 160
        rule.mainClock.advanceTimeByFrame()
        assertTrue(scrollState.canScrollForward)

        rule.onNodeWithTag("container").assertWidthIsEqualTo(150.dp)
        rule.onNodeWithTag("column").assertWidthIsEqualTo(150.dp - scrollbarWidth)
        rule.onNodeWithTag("item-0").assertWidthIsEqualTo(150.dp - scrollbarWidth)
    }

    @Test
    fun `scrolling vertical container should respect width constraints when is AlwaysVisible (Win+Linux)`() {
        if (hostOs == OS.MacOS) return

        val scrollState = ScrollState(0)
        val itemWidth = mutableIntStateOf(20)
        rule.setContent {
            IntUiTestTheme {
                val scrollbarStyle by rememberScrollbarStyle(alwaysVisible = true)

                VerticallyScrollableContainer(
                    modifier = Modifier.testTag("container").widthIn(min = 50.dp, max = 150.dp).height(200.dp),
                    scrollState = scrollState,
                    style = scrollbarStyle,
                ) {
                    Column(modifier = Modifier.testTag("column")) {
                        repeat(100) {
                            Text(
                                "Item $it",
                                modifier = Modifier.size(itemWidth.value.dp, 20.dp).testTag("item-$it"),
                                maxLines = 1,
                            )
                        }
                    }
                }
            }
        }

        assertTrue(scrollState.canScrollForward)

        // Start with an item narrower than the minWidth of 50.dp
        rule.onNodeWithTag("container").assertWidthIsEqualTo(50.dp)
        rule.onNodeWithTag("column").assertWidthIsEqualTo(20.dp)
        rule.onNodeWithTag("item-0").assertWidthIsEqualTo(20.dp)

        // Then make the item exactly as wide as the minWidth of 50.dp
        itemWidth.value = 50
        rule.mainClock.advanceTimeByFrame()
        assertTrue(scrollState.canScrollForward)

        rule.onNodeWithTag("container").assertWidthIsEqualTo(50.dp)
        rule.onNodeWithTag("column").assertWidthIsEqualTo(50.dp)
        rule.onNodeWithTag("item-0").assertWidthIsEqualTo(50.dp)

        // Then make the item exactly as wide as the maxWidth of 150.dp
        itemWidth.value = 150
        rule.mainClock.advanceTimeByFrame()
        assertTrue(scrollState.canScrollForward)

        rule.onNodeWithTag("container").assertWidthIsEqualTo(150.dp)
        rule.onNodeWithTag("column").assertWidthIsEqualTo(150.dp)
        rule.onNodeWithTag("item-0").assertWidthIsEqualTo(150.dp)

        // Lastly, make the item wider than the maxWidth of 150.dp
        itemWidth.value = 160
        rule.mainClock.advanceTimeByFrame()
        assertTrue(scrollState.canScrollForward)

        rule.onNodeWithTag("container").assertWidthIsEqualTo(150.dp)
        rule.onNodeWithTag("column").assertWidthIsEqualTo(150.dp)
        rule.onNodeWithTag("item-0").assertWidthIsEqualTo(150.dp)
    }

    @Test
    fun `non-scrolling vertical container should respect width constraints when is WhenScrolling`() {
        val scrollState = ScrollState(0)
        val itemWidth = mutableIntStateOf(20)
        rule.setContent {
            IntUiTestTheme {
                val scrollbarStyle by rememberScrollbarStyle(alwaysVisible = false)

                VerticallyScrollableContainer(
                    modifier = Modifier.testTag("container").widthIn(min = 30.dp, max = 50.dp).height(200.dp),
                    scrollState = scrollState,
                    style = scrollbarStyle,
                ) {
                    Column(modifier = Modifier.testTag("column")) {
                        repeat(3) {
                            Text(
                                "Item $it",
                                modifier = Modifier.size(itemWidth.value.dp, 20.dp).testTag("item-$it"),
                                maxLines = 1,
                            )
                        }
                    }
                }
            }
        }

        assertFalse(scrollState.canScrollForward)

        // Start with an item narrower than the minWidth of 30.dp
        rule.onNodeWithTag("container").assertWidthIsEqualTo(30.dp)
        rule.onNodeWithTag("column").assertWidthIsEqualTo(20.dp)
        rule.onNodeWithTag("item-0").assertWidthIsEqualTo(20.dp)

        // Then make the item exactly as wide as the minWidth of 30.dp
        itemWidth.value = 30
        rule.mainClock.advanceTimeByFrame()
        assertFalse(scrollState.canScrollForward)

        rule.onNodeWithTag("container").assertWidthIsEqualTo(30.dp)
        rule.onNodeWithTag("column").assertWidthIsEqualTo(30.dp)
        rule.onNodeWithTag("item-0").assertWidthIsEqualTo(30.dp)

        // Then make the item exactly as wide as the maxWidth of 50.dp
        itemWidth.value = 50
        rule.mainClock.advanceTimeByFrame()
        assertFalse(scrollState.canScrollForward)

        rule.onNodeWithTag("container").assertWidthIsEqualTo(50.dp)
        rule.onNodeWithTag("column").assertWidthIsEqualTo(50.dp)
        rule.onNodeWithTag("item-0").assertWidthIsEqualTo(50.dp)

        // Lastly, make the item wider than the maxWidth of 50.dp
        itemWidth.value = 60
        rule.mainClock.advanceTimeByFrame()
        assertFalse(scrollState.canScrollForward)

        rule.onNodeWithTag("container").assertWidthIsEqualTo(50.dp)
        rule.onNodeWithTag("column").assertWidthIsEqualTo(50.dp)
        rule.onNodeWithTag("item-0").assertWidthIsEqualTo(50.dp)
    }

    @Test
    fun `scrolling vertical container should respect width constraints when is WhenScrolling`() {
        val scrollState = ScrollState(0)
        val itemWidth = mutableIntStateOf(20)
        rule.setContent {
            IntUiTestTheme {
                val scrollbarStyle by rememberScrollbarStyle(alwaysVisible = false)

                VerticallyScrollableContainer(
                    modifier = Modifier.testTag("container").widthIn(min = 50.dp, max = 150.dp).height(200.dp),
                    scrollState = scrollState,
                    style = scrollbarStyle,
                ) {
                    Column(modifier = Modifier.testTag("column")) {
                        repeat(100) {
                            Text(
                                "Item $it",
                                modifier = Modifier.size(itemWidth.value.dp, 20.dp).testTag("item-$it"),
                                maxLines = 1,
                            )
                        }
                    }
                }
            }
        }

        assertTrue(scrollState.canScrollForward)

        // Start with an item narrower than the minWidth of 50.dp
        rule.onNodeWithTag("container").assertWidthIsEqualTo(50.dp)
        rule.onNodeWithTag("column").assertWidthIsEqualTo(20.dp)
        rule.onNodeWithTag("item-0").assertWidthIsEqualTo(20.dp)

        // Then make the item exactly as wide as the minWidth of 50.dp
        itemWidth.value = 50
        rule.mainClock.advanceTimeByFrame()
        assertTrue(scrollState.canScrollForward)

        rule.onNodeWithTag("container").assertWidthIsEqualTo(50.dp)
        rule.onNodeWithTag("column").assertWidthIsEqualTo(50.dp)
        rule.onNodeWithTag("item-0").assertWidthIsEqualTo(50.dp)

        // Then make the item exactly as wide as the maxWidth of 150.dp
        itemWidth.value = 150
        rule.mainClock.advanceTimeByFrame()
        assertTrue(scrollState.canScrollForward)

        rule.onNodeWithTag("container").assertWidthIsEqualTo(150.dp)
        rule.onNodeWithTag("column").assertWidthIsEqualTo(150.dp)
        rule.onNodeWithTag("item-0").assertWidthIsEqualTo(150.dp)

        // Lastly, make the item wider than the maxWidth of 150.dp
        itemWidth.value = 160
        rule.mainClock.advanceTimeByFrame()
        assertTrue(scrollState.canScrollForward)

        rule.onNodeWithTag("container").assertWidthIsEqualTo(150.dp)
        rule.onNodeWithTag("column").assertWidthIsEqualTo(150.dp)
        rule.onNodeWithTag("item-0").assertWidthIsEqualTo(150.dp)
    }

    @Test
    fun `non-scrolling horizontal container should respect height constraints when is AlwaysVisible`() {
        val scrollState = ScrollState(0)
        val itemHeight = mutableIntStateOf(20)
        rule.setContent {
            IntUiTestTheme {
                val scrollbarStyle by rememberScrollbarStyle(alwaysVisible = true)

                HorizontallyScrollableContainer(
                    modifier = Modifier.testTag("container").width(200.dp).heightIn(min = 30.dp, max = 50.dp),
                    scrollState = scrollState,
                    style = scrollbarStyle,
                ) {
                    Row(modifier = Modifier.testTag("row")) {
                        repeat(3) {
                            Text(
                                "Item $it",
                                modifier = Modifier.size(20.dp, itemHeight.value.dp).testTag("item-$it"),
                                maxLines = 1,
                            )
                        }
                    }
                }
            }
        }

        assertFalse(scrollState.canScrollForward)

        // Start with an item shorter than the minHeight of 30.dp
        rule.onNodeWithTag("container").assertHeightIsEqualTo(30.dp)
        rule.onNodeWithTag("row").assertHeightIsEqualTo(20.dp)
        rule.onNodeWithTag("item-0").assertHeightIsEqualTo(20.dp)

        // Then make the item exactly as wide as the minHeight of 30.dp
        itemHeight.value = 30
        rule.mainClock.advanceTimeByFrame()
        assertFalse(scrollState.canScrollForward)

        rule.onNodeWithTag("container").assertHeightIsEqualTo(30.dp)
        rule.onNodeWithTag("row").assertHeightIsEqualTo(30.dp)
        rule.onNodeWithTag("item-0").assertHeightIsEqualTo(30.dp)

        // Then make the item exactly as wide as the maxHeight of 50.dp
        itemHeight.value = 50
        rule.mainClock.advanceTimeByFrame()
        assertFalse(scrollState.canScrollForward)

        rule.onNodeWithTag("container").assertHeightIsEqualTo(50.dp)
        rule.onNodeWithTag("row").assertHeightIsEqualTo(50.dp)
        rule.onNodeWithTag("item-0").assertHeightIsEqualTo(50.dp)

        // Lastly, make the item taller than the maxHeight of 50.dp
        itemHeight.value = 60
        rule.mainClock.advanceTimeByFrame()
        assertFalse(scrollState.canScrollForward)

        rule.onNodeWithTag("container").assertHeightIsEqualTo(50.dp)
        rule.onNodeWithTag("row").assertHeightIsEqualTo(50.dp)
        rule.onNodeWithTag("item-0").assertHeightIsEqualTo(50.dp)
    }

    @Test
    fun `scrolling horizontal container should respect height constraints when is AlwaysVisible (macOS only)`() {
        if (hostOs != OS.MacOS) return

        val scrollState = ScrollState(0)
        val itemHeight = mutableIntStateOf(20)
        rule.setContent {
            IntUiTestTheme {
                val scrollbarStyle by rememberScrollbarStyle(alwaysVisible = true)

                HorizontallyScrollableContainer(
                    modifier = Modifier.testTag("container").width(200.dp).heightIn(min = 50.dp, max = 150.dp),
                    scrollState = scrollState,
                    style = scrollbarStyle,
                ) {
                    Row(modifier = Modifier.testTag("row")) {
                        repeat(100) {
                            Text(
                                "Item $it",
                                modifier = Modifier.size(20.dp, itemHeight.value.dp).testTag("item-$it"),
                                maxLines = 1,
                            )
                        }
                    }
                }
            }
        }

        assertTrue(scrollState.canScrollForward)

        // Start with an item shorter than the minHeight of 50.dp
        rule.onNodeWithTag("container").assertHeightIsEqualTo(50.dp)
        rule.onNodeWithTag("row").assertHeightIsEqualTo(20.dp)
        rule.onNodeWithTag("item-0").assertHeightIsEqualTo(20.dp)

        // Then make the item exactly as wide as the minHeight of 50.dp
        itemHeight.value = 50
        rule.mainClock.advanceTimeByFrame()
        assertTrue(scrollState.canScrollForward)

        val scrollbarHeight = ScrollbarVisibility.AlwaysVisible.default().trackThicknessExpanded
        rule.onNodeWithTag("container").assertHeightIsEqualTo(50.dp + scrollbarHeight)
        rule.onNodeWithTag("row").assertHeightIsEqualTo(50.dp)
        rule.onNodeWithTag("item-0").assertHeightIsEqualTo(50.dp)

        // Then make the item exactly as wide as the maxHeight of 150.dp
        itemHeight.value = 150
        rule.mainClock.advanceTimeByFrame()
        assertTrue(scrollState.canScrollForward)

        rule.onNodeWithTag("container").assertHeightIsEqualTo(150.dp)
        rule.onNodeWithTag("row").assertHeightIsEqualTo(150.dp - scrollbarHeight)
        rule.onNodeWithTag("item-0").assertHeightIsEqualTo(150.dp - scrollbarHeight)

        // Lastly, make the item taller than the maxHeight of 150.dp
        itemHeight.value = 160
        rule.mainClock.advanceTimeByFrame()
        assertTrue(scrollState.canScrollForward)

        rule.onNodeWithTag("container").assertHeightIsEqualTo(150.dp)
        rule.onNodeWithTag("row").assertHeightIsEqualTo(150.dp - scrollbarHeight)
        rule.onNodeWithTag("item-0").assertHeightIsEqualTo(150.dp - scrollbarHeight)
    }

    @Test
    fun `scrolling horizontal container should respect height constraints when is AlwaysVisible (Win+Linux)`() {
        if (hostOs == OS.MacOS) return

        val scrollState = ScrollState(0)
        val itemHeight = mutableIntStateOf(20)
        rule.setContent {
            IntUiTestTheme {
                val scrollbarStyle by rememberScrollbarStyle(alwaysVisible = true)

                HorizontallyScrollableContainer(
                    modifier = Modifier.testTag("container").width(200.dp).heightIn(min = 50.dp, max = 150.dp),
                    scrollState = scrollState,
                    style = scrollbarStyle,
                ) {
                    Row(modifier = Modifier.testTag("row")) {
                        repeat(100) {
                            Text(
                                "Item $it",
                                modifier = Modifier.size(20.dp, itemHeight.value.dp).testTag("item-$it"),
                                maxLines = 1,
                            )
                        }
                    }
                }
            }
        }

        assertTrue(scrollState.canScrollForward)

        // Start with an item shorter than the minHeight of 50.dp
        rule.onNodeWithTag("container").assertHeightIsEqualTo(50.dp)
        rule.onNodeWithTag("row").assertHeightIsEqualTo(20.dp)
        rule.onNodeWithTag("item-0").assertHeightIsEqualTo(20.dp)

        // Then make the item exactly as wide as the minHeight of 50.dp
        itemHeight.value = 50
        rule.mainClock.advanceTimeByFrame()
        assertTrue(scrollState.canScrollForward)

        rule.onNodeWithTag("container").assertHeightIsEqualTo(50.dp)
        rule.onNodeWithTag("row").assertHeightIsEqualTo(50.dp)
        rule.onNodeWithTag("item-0").assertHeightIsEqualTo(50.dp)

        // Then make the item exactly as wide as the maxHeight of 150.dp
        itemHeight.value = 150
        rule.mainClock.advanceTimeByFrame()
        assertTrue(scrollState.canScrollForward)

        rule.onNodeWithTag("container").assertHeightIsEqualTo(150.dp)
        rule.onNodeWithTag("row").assertHeightIsEqualTo(150.dp)
        rule.onNodeWithTag("item-0").assertHeightIsEqualTo(150.dp)

        // Lastly, make the item taller than the maxHeight of 150.dp
        itemHeight.value = 160
        rule.mainClock.advanceTimeByFrame()
        assertTrue(scrollState.canScrollForward)

        rule.onNodeWithTag("container").assertHeightIsEqualTo(150.dp)
        rule.onNodeWithTag("row").assertHeightIsEqualTo(150.dp)
        rule.onNodeWithTag("item-0").assertHeightIsEqualTo(150.dp)
    }

    @Test
    fun `non-scrolling horizontal container should respect height constraints when is WhenScrolling`() {
        val scrollState = ScrollState(0)
        val itemHeight = mutableIntStateOf(20)
        rule.setContent {
            IntUiTestTheme {
                val scrollbarStyle by rememberScrollbarStyle(alwaysVisible = false)

                HorizontallyScrollableContainer(
                    modifier = Modifier.testTag("container").width(200.dp).heightIn(min = 30.dp, max = 50.dp),
                    scrollState = scrollState,
                    style = scrollbarStyle,
                ) {
                    Row(modifier = Modifier.testTag("row")) {
                        repeat(3) {
                            Text(
                                "Item $it",
                                modifier = Modifier.size(20.dp, itemHeight.value.dp).testTag("item-$it"),
                                maxLines = 1,
                            )
                        }
                    }
                }
            }
        }

        assertFalse(scrollState.canScrollForward)

        // Start with an item shorter than the minHeight of 30.dp
        rule.onNodeWithTag("container").assertHeightIsEqualTo(30.dp)
        rule.onNodeWithTag("row").assertHeightIsEqualTo(20.dp)
        rule.onNodeWithTag("item-0").assertHeightIsEqualTo(20.dp)

        // Then make the item exactly as wide as the minHeight of 30.dp
        itemHeight.value = 30
        rule.mainClock.advanceTimeByFrame()
        assertFalse(scrollState.canScrollForward)

        rule.onNodeWithTag("container").assertHeightIsEqualTo(30.dp)
        rule.onNodeWithTag("row").assertHeightIsEqualTo(30.dp)
        rule.onNodeWithTag("item-0").assertHeightIsEqualTo(30.dp)

        // Then make the item exactly as wide as the maxHeight of 50.dp
        itemHeight.value = 50
        rule.mainClock.advanceTimeByFrame()
        assertFalse(scrollState.canScrollForward)

        rule.onNodeWithTag("container").assertHeightIsEqualTo(50.dp)
        rule.onNodeWithTag("row").assertHeightIsEqualTo(50.dp)
        rule.onNodeWithTag("item-0").assertHeightIsEqualTo(50.dp)

        // Lastly, make the item taller than the maxHeight of 50.dp
        itemHeight.value = 60
        rule.mainClock.advanceTimeByFrame()
        assertFalse(scrollState.canScrollForward)

        rule.onNodeWithTag("container").assertHeightIsEqualTo(50.dp)
        rule.onNodeWithTag("row").assertHeightIsEqualTo(50.dp)
        rule.onNodeWithTag("item-0").assertHeightIsEqualTo(50.dp)
    }

    @Test
    fun `scrolling horizontal container should respect height constraints when is WhenScrolling`() {
        val scrollState = ScrollState(0)
        val itemHeight = mutableIntStateOf(20)
        rule.setContent {
            IntUiTestTheme {
                val scrollbarStyle by rememberScrollbarStyle(alwaysVisible = false)

                HorizontallyScrollableContainer(
                    modifier = Modifier.testTag("container").width(200.dp).heightIn(min = 50.dp, max = 150.dp),
                    scrollState = scrollState,
                    style = scrollbarStyle,
                ) {
                    Row(modifier = Modifier.testTag("row")) {
                        repeat(100) {
                            Text(
                                "Item $it",
                                modifier = Modifier.size(20.dp, itemHeight.value.dp).testTag("item-$it"),
                                maxLines = 1,
                            )
                        }
                    }
                }
            }
        }

        assertTrue(scrollState.canScrollForward)

        // Start with an item shorter than the minHeight of 50.dp
        rule.onNodeWithTag("container").assertHeightIsEqualTo(50.dp)
        rule.onNodeWithTag("row").assertHeightIsEqualTo(20.dp)
        rule.onNodeWithTag("item-0").assertHeightIsEqualTo(20.dp)

        // Then make the item exactly as wide as the minHeight of 50.dp
        itemHeight.value = 50
        rule.mainClock.advanceTimeByFrame()
        assertTrue(scrollState.canScrollForward)

        rule.onNodeWithTag("container").assertHeightIsEqualTo(50.dp)
        rule.onNodeWithTag("row").assertHeightIsEqualTo(50.dp)
        rule.onNodeWithTag("item-0").assertHeightIsEqualTo(50.dp)

        // Then make the item exactly as wide as the maxHeight of 150.dp
        itemHeight.value = 150
        rule.mainClock.advanceTimeByFrame()
        assertTrue(scrollState.canScrollForward)

        rule.onNodeWithTag("container").assertHeightIsEqualTo(150.dp)
        rule.onNodeWithTag("row").assertHeightIsEqualTo(150.dp)
        rule.onNodeWithTag("item-0").assertHeightIsEqualTo(150.dp)

        // Lastly, make the item taller than the maxHeight of 150.dp
        itemHeight.value = 160
        rule.mainClock.advanceTimeByFrame()
        assertTrue(scrollState.canScrollForward)

        rule.onNodeWithTag("container").assertHeightIsEqualTo(150.dp)
        rule.onNodeWithTag("row").assertHeightIsEqualTo(150.dp)
        rule.onNodeWithTag("item-0").assertHeightIsEqualTo(150.dp)
    }

    @Composable
    private fun rememberScrollbarStyle(
        alwaysVisible: Boolean,
        baseStyle: ScrollbarStyle = JewelTheme.scrollbarStyle,
        clickBehavior: TrackClickBehavior = baseStyle.trackClickBehavior,
        alwaysVisibleScrollbarVisibility: ScrollbarVisibility = ScrollbarVisibility.AlwaysVisible.default(),
        whenScrollingScrollbarVisibility: ScrollbarVisibility = ScrollbarVisibility.WhenScrolling.default(),
    ) =
        rememberUpdatedState(
            if (alwaysVisible) {
                ScrollbarStyle(
                    colors = baseStyle.colors,
                    metrics = baseStyle.metrics,
                    trackClickBehavior = clickBehavior,
                    scrollbarVisibility = alwaysVisibleScrollbarVisibility,
                )
            } else {
                ScrollbarStyle(
                    colors = baseStyle.colors,
                    metrics = baseStyle.metrics,
                    trackClickBehavior = clickBehavior,
                    scrollbarVisibility = whenScrollingScrollbarVisibility,
                )
            }
        )

    @Suppress("SpellCheckingInspection")
    private val loremIpsum =
        """
        Lorem ipsum dolor sit amet, consectetur adipiscing elit.
        Sed auctor, neque in accumsan vehicula, enim purus vestibulum odio, non tristique dolor quam vel ipsum. 
        Proin egestas, orci id hendrerit bibendum, nisl neque imperdiet nisl, a euismod nibh diam nec lectus. 
        Duis euismod, quam nec aliquam iaculis, dolor lorem bibendum turpis, vel malesuada augue sapien vel mi. 
        Quisque ut facilisis nibh. Maecenas euismod hendrerit sem, ac scelerisque odio auctor nec. 
        Sed sit amet consequat eros. Donec nisl tellus, accumsan nec ligula in, eleifend sodales sem. 
        Sed malesuada, nulla ac eleifend fermentum, nibh mi consequat quam, quis convallis lacus nunc eu dui. 
        Pellentesque eget enim quis orci porttitor consequat sed sed quam. 
        Sed aliquam, nisl et lacinia lacinia, diam nunc laoreet nisi, sit amet consectetur dolor lorem et sem. 
        Duis ultricies, mauris in aliquam interdum, orci nulla finibus massa, a tristique urna sapien vel quam. 
        Sed nec sapien nec dui rhoncus bibendum. Sed blandit bibendum libero.
        """
            .trimIndent()
}
