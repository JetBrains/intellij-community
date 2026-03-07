// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jewel.foundation.lazy

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredHeight
import androidx.compose.foundation.text.BasicText
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.withKeyDown
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@Suppress("ImplicitUnitReturnType")
internal class SingleAndMultiSelectionLazyColumnTest {
    @get:Rule val composeRule = createComposeRule()

    private val items = listOf("A", "B", "C", "D")

    @Test
    fun `SingleSelectionLazyColumn keeps one selected key and emits single index`() = runTest {
        lateinit var state: SingleSelectionLazyListState
        val callbackInvocations = mutableListOf<List<Int>>()

        composeRule.setContent {
            state = rememberSingleSelectionLazyListState(initialSelectedKey = "A")
            Box(modifier = Modifier.requiredHeight(300.dp)) {
                SingleSelectionLazyColumn(
                    modifier = Modifier.testTag("list"),
                    state = state,
                    onSelectedIndexesChange = { callbackInvocations += it },
                ) {
                    items(items.size, key = { items[it] }) { index ->
                        BasicText(items[index], modifier = Modifier.testTag(items[index]))
                    }
                }
            }
        }

        composeRule.awaitIdle()
        assertEquals(setOf("A"), state.selectedKeys)
        assertTrue(callbackInvocations.isEmpty())

        composeRule.onNodeWithTag("C", useUnmergedTree = true).performClick()
        composeRule.awaitIdle()
        assertEquals(setOf("C"), state.selectedKeys)
        assertEquals(listOf(listOf(2)), callbackInvocations)

        composeRule.onNodeWithTag("B", useUnmergedTree = true).performClick()
        composeRule.awaitIdle()
        assertEquals(setOf("B"), state.selectedKeys)
        assertEquals(listOf(listOf(2), listOf(1)), callbackInvocations)
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun `MultiSelectionLazyColumn extends selection with shift arrow and emits updated indices`() = runTest {
        lateinit var state: MultiSelectionLazyListState
        val callbackInvocations = mutableListOf<List<Int>>()

        composeRule.setContent {
            state = rememberMultiSelectionLazyListState()
            Box(modifier = Modifier.requiredHeight(300.dp)) {
                MultiSelectionLazyColumn(
                    modifier = Modifier.testTag("list"),
                    state = state,
                    onSelectedIndexesChange = { callbackInvocations += it },
                ) {
                    items(items.size, key = { items[it] }) { index ->
                        BasicText(items[index], modifier = Modifier.testTag(items[index]))
                    }
                }
            }
        }

        composeRule.awaitIdle()
        assertTrue(callbackInvocations.isEmpty())

        composeRule.onNodeWithTag("B", useUnmergedTree = true).performClick()
        composeRule.awaitIdle()
        assertEquals(setOf("B"), state.selectedKeys)
        assertEquals(listOf(listOf(1)), callbackInvocations)

        composeRule.onNodeWithTag("list").performKeyInput { withKeyDown(Key.ShiftLeft) { pressKey(Key.DirectionDown) } }
        composeRule.awaitIdle()

        assertEquals(setOf("B", "C"), state.selectedKeys)
        assertEquals(listOf(listOf(1), listOf(1, 2)), callbackInvocations)
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun `MultiSelectionLazyColumn keeps non-contiguous picks and adds next item on shift down`() = runTest {
        val localItems = listOf("1", "2", "3", "4", "5")
        lateinit var state: MultiSelectionLazyListState
        val callbackInvocations = mutableListOf<List<Int>>()

        composeRule.setContent {
            state = rememberMultiSelectionLazyListState()
            Box(modifier = Modifier.requiredHeight(300.dp)) {
                MultiSelectionLazyColumn(
                    modifier = Modifier.testTag("list"),
                    state = state,
                    onSelectedIndexesChange = { callbackInvocations += it },
                ) {
                    items(localItems.size, key = { localItems[it] }) { index ->
                        BasicText(localItems[index], modifier = Modifier.testTag(localItems[index]))
                    }
                }
            }
        }

        composeRule.awaitIdle()
        // Ensure the list has keyboard focus before sending key events.
        composeRule.onNodeWithTag("3", useUnmergedTree = true).performClick()
        composeRule.awaitIdle()
        composeRule.runOnIdle {
            state.selectedKeys = setOf("1", "3")
            state.lastActiveItemIndex = 2
        }
        composeRule.awaitIdle()
        callbackInvocations.clear()

        composeRule.onNodeWithTag("list").performKeyInput { withKeyDown(Key.ShiftLeft) { pressKey(Key.DirectionDown) } }
        composeRule.awaitIdle()

        assertEquals(setOf("1", "3", "4"), state.selectedKeys)
        assertEquals(listOf(listOf(0, 2, 3)), callbackInvocations)
    }
}
