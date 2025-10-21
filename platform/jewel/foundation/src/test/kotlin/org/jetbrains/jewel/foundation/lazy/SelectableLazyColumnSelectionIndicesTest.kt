// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jewel.foundation.lazy

import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.junit4.createComposeRule
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/**
 * Verifies that SelectableLazyColumn re-emits onSelectedIndexesChange when the list order (key→index mapping) changes
 * while the selected keys remain the same.
 *
 * Scenario:
 * - We select the item with key "B" before composition settles.
 * - Initially, items are ["A", "B", "C"], so "B" is at index 1 and the callback emits [1].
 * - We then reorder items to ["C", "A", "B"]: keys are unchanged, but "B" moves to index 2.
 *
 * Expected:
 * - onSelectedIndexesChange is invoked again with the updated index [2].
 *
 * Why this matters:
 * - If emission is gated only by equality of selectedKeys, a pure reorder won’t trigger the callback, leaving consumers
 *   that rely on indices (e.g., ListComboBox) with stale selection positions.
 */
public class SelectableLazyColumnSelectionIndicesTest {
    @get:Rule public val composeTestRule: ComposeContentTestRule = createComposeRule()

    @Test
    public fun `when list order changes but selected keys don't onSelectedIndexesChange is emitted with new index`() {
        val initialItems = listOf("A", "B", "C")
        val reorderedItems = listOf("C", "A", "B")

        var lastIndices: List<Int>? = null
        var emissionCount = 0
        var itemsStateRef: MutableState<List<String>>? = null

        composeTestRule.setContent {
            val state = rememberSelectableLazyListState()
            val itemsState: MutableState<List<String>> = remember { mutableStateOf(initialItems) }

            // Select "B" by key (key = the item string)
            LaunchedEffect(Unit) { state.selectedKeys = setOf("B") }

            SelectableLazyColumn(
                state = state,
                onSelectedIndexesChange = { indices ->
                    lastIndices = indices
                    emissionCount++
                },
            ) {
                itemsIndexed(
                    items = itemsState.value,
                    key = { _, item -> item }, // keys are the item strings
                ) { _, _ ->
                    // content not relevant for this test
                }
            }
            // Expose items state to the test scope to mutate during assertions
            itemsStateRef = itemsState
        }

        // Let initial selection propagate
        composeTestRule.waitForIdle()
        // We expect at least one emission with index 1 ("B" at position 1)
        assertEquals(listOf(1), lastIndices)

        // Change list order so that mapping key->index changes; selectedKeys stay the same (setOf("B"))
        composeTestRule.runOnIdle {
            itemsStateRef?.value = reorderedItems // "B" is now at index 2
        }

        // With the bug, there will be no new emission (guarded by lastSelectedKeys == state.selectedKeys)
        // With the fix, we expect a new emission with the updated index 2.
        composeTestRule.waitForIdle()

        // Assert we observed a second emission reflecting the new index of key "B"
        assertEquals("Expected two emissions: initial and after reorder", 2, emissionCount)
        assertEquals(listOf(2), lastIndices)
    }

    @Test
    public fun `when header is inserted but selected keys remain the same onSelectedIndexesChange is emitted with new index`() {
        val initialItems = listOf("A", "B", "C")
        val headerItems = listOf("HEADER", "A", "B", "C")

        var lastIndices: List<Int>? = null
        var emissionCount = 0
        var itemsStateRef: MutableState<List<String>>? = null

        composeTestRule.setContent {
            val state = rememberSelectableLazyListState()
            val itemsState: MutableState<List<String>> = remember { mutableStateOf(initialItems) }

            // Select "B" by key (key = the item string)
            LaunchedEffect(Unit) { state.selectedKeys = setOf("B") }

            SelectableLazyColumn(
                state = state,
                onSelectedIndexesChange = { indices ->
                    lastIndices = indices
                    emissionCount++
                },
            ) {
                itemsIndexed(items = itemsState.value, key = { _, item -> item }) { _, _ ->
                    // content not relevant for this test
                }
            }

            itemsStateRef = itemsState
        }

        // Let initial selection propagate
        composeTestRule.waitForIdle()
        assertEquals(listOf(1), lastIndices)

        // Insert a new item at the top; selected key remains "B" but its index shifts by +1
        composeTestRule.runOnIdle { itemsStateRef?.value = headerItems }

        composeTestRule.waitForIdle()

        // Expect a second emission with the updated index (from 1 to 2)
        assertEquals("Expected two emissions: initial and after header insert", 2, emissionCount)
        assertEquals(listOf(2), lastIndices)
    }

    @Test
    public fun `when an item before the selected key is removed onSelectedIndexesChange is emitted with new index`() {
        val initialItems = listOf("A", "B", "C", "D")
        val removedAItems = listOf("B", "C", "D") // remove an item before the selected one

        var lastIndices: List<Int>? = null
        var emissionCount = 0
        var itemsStateRef: MutableState<List<String>>? = null

        composeTestRule.setContent {
            val state = rememberSelectableLazyListState()
            val itemsState: MutableState<List<String>> = remember { mutableStateOf(initialItems) }

            // Select "C" by key; initially at index 2
            LaunchedEffect(Unit) { state.selectedKeys = setOf("C") }

            SelectableLazyColumn(
                state = state,
                onSelectedIndexesChange = { indices ->
                    lastIndices = indices
                    emissionCount++
                },
            ) {
                itemsIndexed(items = itemsState.value, key = { _, item -> item }) { _, _ ->
                    // content not relevant for this test
                }
            }

            itemsStateRef = itemsState
        }

        // Wait for the initial emission ("C" at index 2)
        composeTestRule.waitForIdle()
        assertEquals(listOf(2), lastIndices)

        // Remove an item that sits before the selected key (remove "A") -> "C" shifts from 2 to 1
        composeTestRule.runOnIdle { itemsStateRef?.value = removedAItems }

        composeTestRule.waitForIdle()

        // Expect a second emission reflecting the new index of "C"
        assertEquals("Expected two emissions: initial and after removal before selection", 2, emissionCount)
        assertEquals(listOf(1), lastIndices)
    }
}
