// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jewel.foundation.lazy

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredHeight
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/**
 * Verifies that [SelectionMode.None] prevents any selection from happening, both on pointer click and on initial focus.
 */
@Suppress("ImplicitUnitReturnType")
internal class SelectableLazyColumnSelectionModeNoneTest {
    @get:Rule val composeRule = createComposeRule()

    private val items = (0..10).toList()

    @Composable
    private fun NoSelectionSelectableLazyColumn(
        state: SelectableLazyListState,
        onSelectedIndexesChange: (List<Int>) -> Unit = {},
        content: SelectableLazyListScope.() -> Unit,
    ) {
        Box(modifier = Modifier.requiredHeight(300.dp)) {
            SelectableLazyColumn(
                modifier = Modifier.testTag("list"),
                selectionMode = SelectionMode.None,
                state = state,
                onSelectedIndexesChange = onSelectedIndexesChange,
            ) {
                content()
            }
        }
    }

    @Test
    fun `clicking an item in SelectionMode-None mode does not produce any selection`() = runTest {
        lateinit var state: SelectableLazyListState
        composeRule.setContent {
            state = rememberSelectableLazyListState()
            NoSelectionSelectableLazyColumn(state) {
                items(items.size, key = { items[it] }) { index ->
                    val tag = "Item ${items[index]}"
                    BasicText(tag, modifier = Modifier.testTag(tag))
                }
            }
        }
        composeRule.awaitIdle()

        composeRule.onNodeWithTag("Item 3", useUnmergedTree = true).performClick()
        composeRule.awaitIdle()

        assertEquals(emptySet<Any>(), state.selectedKeys)
    }

    @Test
    fun `clicking an item in SelectionMode-None mode does not trigger onSelectedIndexesChange`() = runTest {
        val callbackInvocations = mutableListOf<List<Int>>()
        composeRule.setContent {
            val state = rememberSelectableLazyListState()
            NoSelectionSelectableLazyColumn(state, onSelectedIndexesChange = { callbackInvocations += it }) {
                items(items.size, key = { items[it] }) { index ->
                    val tag = "Item ${items[index]}"
                    BasicText(tag, modifier = Modifier.testTag(tag))
                }
            }
        }
        composeRule.awaitIdle()

        composeRule.onNodeWithTag("Item 5", useUnmergedTree = true).performClick()
        composeRule.awaitIdle()

        assertEquals(emptyList<List<Int>>(), callbackInvocations)
    }

    @Test
    fun `focusing the list in SelectionMode-None mode does not auto-select the first item`() = runTest {
        lateinit var state: SelectableLazyListState
        composeRule.setContent {
            state = rememberSelectableLazyListState()
            NoSelectionSelectableLazyColumn(state) {
                items(items.size, key = { items[it] }) { index ->
                    val tag = "Item ${items[index]}"
                    BasicText(tag, modifier = Modifier.testTag(tag))
                }
            }
        }
        composeRule.awaitIdle()

        assertEquals(emptySet<Any>(), state.selectedKeys)
    }
}
