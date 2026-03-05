// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jewel.foundation.lazy

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.createComposeRule
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

@Suppress("ImplicitUnitReturnType")
internal class SingleAndMultiSelectionLazyListStateTest {
    @get:Rule val composeRule = createComposeRule()

    @Test
    fun `SingleSelectionLazyListState starts empty when no initial key is provided`() {
        val state = SingleSelectionLazyListState(lazyListState = LazyListState())

        assertEquals(SelectionMode.Single, state.selectionMode)
        assertEquals(emptySet<Any>(), state.selectedKeys)
    }

    @Test
    fun `SingleSelectionLazyListState keeps only first key when assigning multiple keys`() {
        val state = SingleSelectionLazyListState(lazyListState = LazyListState())

        state.selectedKeys = linkedSetOf("A", "B", "C")

        assertEquals(setOf("A"), state.selectedKeys)
    }

    @Test
    fun `SingleSelectionLazyListState can be cleared`() {
        val state = SingleSelectionLazyListState(lazyListState = LazyListState(), initialSelectedKey = "A")

        state.selectedKeys = emptySet()

        assertEquals(emptySet<Any>(), state.selectedKeys)
    }

    @Test
    fun `MultiSelectionLazyListState preserves all selected keys`() {
        val state = MultiSelectionLazyListState(lazyListState = LazyListState(), initialSelectedKeys = setOf("A", "C"))

        assertEquals(SelectionMode.Multiple, state.selectionMode)
        assertEquals(setOf("A", "C"), state.selectedKeys)
    }

    @Test
    fun `MultiSelectionLazyListState updates and clears selected keys`() {
        val state = MultiSelectionLazyListState(lazyListState = LazyListState())

        state.selectedKeys = linkedSetOf("A", "B")
        assertEquals(setOf("A", "B"), state.selectedKeys)

        state.selectedKeys = emptySet()
        assertEquals(emptySet<Any>(), state.selectedKeys)
    }

    @Test
    fun `rememberSingleSelectionLazyListState applies initial key only once`() {
        var initialSelectedKey: Any? by mutableStateOf("A")
        lateinit var state: SingleSelectionLazyListState

        composeRule.setContent { state = rememberSingleSelectionLazyListState(initialSelectedKey = initialSelectedKey) }
        composeRule.waitForIdle()
        assertEquals(setOf("A"), state.selectedKeys)

        composeRule.runOnIdle { initialSelectedKey = "B" }
        composeRule.waitForIdle()

        assertEquals(setOf("A"), state.selectedKeys)
    }

    @Test
    fun `rememberMultiSelectionLazyListState applies initial keys only once`() {
        var initialSelectedKeys by mutableStateOf(listOf("A"))
        lateinit var state: MultiSelectionLazyListState

        composeRule.setContent {
            state = rememberMultiSelectionLazyListState(initialSelectedKeys = initialSelectedKeys.toSet())
        }
        composeRule.waitForIdle()
        assertEquals(setOf("A"), state.selectedKeys)

        composeRule.runOnIdle { initialSelectedKeys = listOf("B", "C") }
        composeRule.waitForIdle()

        assertEquals(setOf("A"), state.selectedKeys)
    }

    @Test
    fun `rememberSelectableLazyListState with SelectionMode Single keeps only first initial key`() {
        lateinit var state: SelectableLazyListState

        composeRule.setContent {
            state =
                rememberSelectableLazyListState(
                    selectionMode = SelectionMode.Single,
                    initialSelectedKeys = listOf("A", "B", "C"),
                )
        }
        composeRule.waitForIdle()

        assertEquals(setOf("A"), state.selectedKeys)
    }

    @Test
    fun `rememberSelectableLazyListState with SelectionMode None drops all initial keys`() {
        lateinit var state: SelectableLazyListState

        composeRule.setContent {
            state =
                rememberSelectableLazyListState(
                    selectionMode = SelectionMode.None,
                    initialSelectedKeys = listOf("A", "B"),
                )
        }
        composeRule.waitForIdle()

        assertEquals(emptySet<Any>(), state.selectedKeys)
    }

    @Test
    fun `rememberSelectableLazyListState with SelectionMode Multiple deduplicates while preserving insertion order`() {
        lateinit var state: SelectableLazyListState

        composeRule.setContent {
            state =
                rememberSelectableLazyListState(
                    selectionMode = SelectionMode.Multiple,
                    initialSelectedKeys = listOf("B", "A", "B", "C", "A"),
                )
        }
        composeRule.waitForIdle()

        assertEquals(listOf("B", "A", "C"), state.selectedKeys.toList())
    }

    @Test
    fun `rememberSelectableLazyListState legacy two-arg overload delegates to four-arg defaults with SelectionMode Multiple`() {
        lateinit var state: SelectableLazyListState

        composeRule.setContent { state = rememberSelectableLazyListState(3, 7) }
        composeRule.waitForIdle()

        assertEquals(SelectionMode.Multiple, state.selectionMode)
        assertEquals(emptySet<Any>(), state.selectedKeys)
        assertEquals(3, state.firstVisibleItemIndex)
        assertEquals(7, state.firstVisibleItemScrollOffset)
    }
}
