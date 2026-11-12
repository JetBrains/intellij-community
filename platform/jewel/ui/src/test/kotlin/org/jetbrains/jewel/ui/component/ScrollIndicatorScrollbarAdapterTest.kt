// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jewel.ui.component

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.staggeredgrid.LazyStaggeredGridState
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.rememberLazyStaggeredGridState
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlin.coroutines.EmptyCoroutineContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.runBlocking
import org.jetbrains.jewel.BasicJewelUiTest
import org.jetbrains.jewel.foundation.scroll.Scroll2DState
import org.junit.Assert.assertEquals
import org.junit.Test

public class ScrollIndicatorScrollbarAdapterTest : BasicJewelUiTest() {
    @Test
    public fun `a fractional target does not move the content back and forth`() {
        val state = Scroll2DState()
        val axis = state.verticalScrollableState
        val adapter = ScrollIndicatorScrollbarAdapter(axis, checkNotNull(axis.scrollIndicatorState), false)

        val positions = runBlocking {
            List(4) {
                adapter.scrollTo(10.4)
                state.value.y
            }
        }

        assertEquals(listOf(10, 10, 10, 10), positions)
    }

    @Test
    public fun `a target that is not a finite number does not scroll`() {
        val state = Scroll2DState()
        val axis = state.verticalScrollableState
        val adapter = ScrollIndicatorScrollbarAdapter(axis, checkNotNull(axis.scrollIndicatorState), false)

        runBlocking {
            adapter.scrollTo(Double.NaN)
            adapter.scrollTo(Double.POSITIVE_INFINITY)
        }

        assertEquals(0, state.value.y)
    }

    @Test
    public fun `a reversed layout reports the offset from the first item`() {
        val grid = setReversedGridContent()
        val adapter = ScrollIndicatorScrollbarAdapter(grid, checkNotNull(grid.scrollIndicatorState), true)
        val itemHeight = with(composeRule.density) { ITEM_HEIGHT.roundToPx() }

        assertEquals(0.0, adapter.scrollOffset, 0.0)

        composeRule.runOnIdle { runBlocking { grid.scrollToItem(5) } }
        composeRule.waitForIdle()

        assertEquals(5.0 * itemHeight, adapter.scrollOffset, 0.0)
    }

    @Test
    public fun `the thumb of a reversed layout starts at the end of the track`() {
        val grid = setReversedGridContent()
        val adapter = ScrollIndicatorScrollbarAdapter(grid, checkNotNull(grid.scrollIndicatorState), true)
        val slider =
            SliderAdapter(
                adapter = adapter,
                trackSize = TRACK_SIZE,
                minHeight = 0f,
                reverseLayout = true,
                isVertical = true,
                // The test only reads the thumb position, so nothing runs in this scope.
                coroutineScope = CoroutineScope(EmptyCoroutineContext),
                scrollState = grid,
            )

        assertEquals(TRACK_SIZE - slider.thumbSize, slider.position, 0.5)
    }

    @Test
    public fun `scrollTo in a reversed layout moves to the offset from the first item`() {
        val grid = setReversedGridContent()
        val adapter = ScrollIndicatorScrollbarAdapter(grid, checkNotNull(grid.scrollIndicatorState), true)
        val itemHeight = with(composeRule.density) { ITEM_HEIGHT.roundToPx() }

        composeRule.runOnIdle { runBlocking { adapter.scrollTo(3.0 * itemHeight) } }
        composeRule.waitForIdle()

        assertEquals(3, grid.firstVisibleItemIndex)
        assertEquals(3.0 * itemHeight, adapter.scrollOffset, 0.0)
    }

    private fun setReversedGridContent(): LazyStaggeredGridState {
        lateinit var grid: LazyStaggeredGridState
        composeRule.setContent {
            grid = rememberLazyStaggeredGridState()
            LazyVerticalStaggeredGrid(
                columns = StaggeredGridCells.Fixed(1),
                modifier = Modifier.size(100.dp),
                state = grid,
                reverseLayout = true,
            ) {
                items(ITEM_COUNT) { Box(Modifier.fillMaxWidth().height(ITEM_HEIGHT)) }
            }
        }
        composeRule.waitForIdle()
        return grid
    }

    private companion object {
        const val ITEM_COUNT = 50
        const val TRACK_SIZE = 100
        val ITEM_HEIGHT = 20.dp
    }
}
