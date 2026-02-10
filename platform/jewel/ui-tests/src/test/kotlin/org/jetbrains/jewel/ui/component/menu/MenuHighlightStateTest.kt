// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jewel.ui.component.menu

import kotlin.test.BeforeTest
import kotlin.test.assertEquals
import kotlin.test.assertNull
import org.junit.Assert
import org.junit.Test

internal class MenuHighlightStateTest {
    private lateinit var state: MenuHighlightState

    @BeforeTest
    fun setUp() {
        state = MenuHighlightState()
    }

    @Test
    fun `initial state has no highlighted item`() {
        assertNull(state.highlightedItemIndex)
    }

    @Test
    fun `highlightItem sets the highlighted index`() {
        state.highlightItem(3)
        assertEquals(3, state.highlightedItemIndex)
    }

    @Test
    fun `highlightItem can update to different index`() {
        state.highlightItem(1)
        assertEquals(1, state.highlightedItemIndex)

        state.highlightItem(5)
        assertEquals(5, state.highlightedItemIndex)
    }

    @Test
    fun `highlightItem can set index to zero`() {
        state.highlightItem(0)
        assertEquals(0, state.highlightedItemIndex)
    }

    @Test
    fun `highlightItem accepts null to clear highlight`() {
        state.highlightItem(2)
        assertEquals(2, state.highlightedItemIndex)

        state.highlightItem(null)
        assertNull(state.highlightedItemIndex)
    }

    @Test
    fun `clearHighlight removes the highlighted index`() {
        state.highlightItem(4)
        assertEquals(4, state.highlightedItemIndex)

        state.clearHighlight()
        assertNull(state.highlightedItemIndex)
    }

    @Test
    fun `clearHighlight on empty state does nothing`() {
        state.clearHighlight()
        assertNull(state.highlightedItemIndex)
    }

    @Test
    fun `multiple highlight operations work correctly`() {
        state.highlightItem(0)
        assertEquals(0, state.highlightedItemIndex)

        state.highlightItem(1)
        assertEquals(1, state.highlightedItemIndex)

        state.clearHighlight()
        assertNull(state.highlightedItemIndex)

        state.highlightItem(2)
        assertEquals(2, state.highlightedItemIndex)

        state.highlightItem(null)
        assertNull(state.highlightedItemIndex)
    }

    @Test
    fun `separate MenuHighlightState instances are independent`() {
        val state1 = state
        val state2 = MenuHighlightState()

        state1.highlightItem(2)
        state2.highlightItem(5)

        Assert.assertEquals(2, state1.highlightedItemIndex)
        Assert.assertEquals(5, state2.highlightedItemIndex)

        state1.clearHighlight()

        Assert.assertNull(state1.highlightedItemIndex)
        Assert.assertEquals(5, state2.highlightedItemIndex)
    }
}
