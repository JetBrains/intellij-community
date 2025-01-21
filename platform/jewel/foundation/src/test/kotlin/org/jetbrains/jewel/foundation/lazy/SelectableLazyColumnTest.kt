package org.jetbrains.jewel.foundation.lazy

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredHeight
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.mutableStateOf
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
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

internal class SelectableLazyColumnTest {
    @get:Rule val composeRule = createComposeRule()

    @Test
    fun `column with multiple items`() =
        runBlocking<Unit> {
            val items1 = (0..10).toList()
            val items2 = (11..50).toList()
            val scrollState = SelectableLazyListState(LazyListState())
            composeRule.setContent {
                Box(modifier = Modifier.requiredHeight(100.dp)) {
                    SelectableLazyColumn(state = scrollState) {
                        items(items1.size, key = { items1[it] }) {
                            val itemText = "Item ${items1[it]}"
                            BasicText(itemText, modifier = Modifier.testTag(itemText))
                        }

                        items(items2.size, key = { items2[it] }) {
                            val itemText = "Item ${items2[it]}"
                            BasicText(itemText, modifier = Modifier.testTag(itemText))
                        }
                    }
                }
            }
            composeRule.awaitIdle()
            composeRule.onNodeWithTag("Item 20").assertDoesNotExist()
            scrollState.scrollToItem(20)
            composeRule.onNodeWithTag("Item 20").assertExists()
        }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun `selection with arrow keys`() = runBlocking {
        val items = (0..10).toList()
        val state = SelectableLazyListState(LazyListState())
        composeRule.setContent {
            Box(modifier = Modifier.requiredHeight(300.dp)) {
                SelectableLazyColumn(state = state, modifier = Modifier.testTag("list")) {
                    items(items.size, key = { items[it] }) {
                        val itemText = "Item ${items[it]}"
                        BasicText(itemText, modifier = Modifier.testTag(itemText))
                    }
                }
            }
        }
        composeRule.awaitIdle()
        // select item 5 by click
        composeRule.onNodeWithTag("Item 5").assertExists()
        composeRule.onNodeWithTag("Item 5").performClick()

        // check that 5th element is selected
        assertEquals(1, state.selectedKeys.size)
        assertEquals(items[5], state.selectedKeys.single())

        // press arrow up and check that selected key is changed
        repeat(20) { step ->
            composeRule.onNodeWithTag("list").performKeyInput { pressKey(Key.DirectionUp) }

            // check that previous element is selected
            // when started from 5th element
            assertTrue(state.selectedKeys.size == 1)
            val expectedSelectedIndex = (5 - step - 1).takeIf { it >= 0 } ?: 0
            assertEquals(items[expectedSelectedIndex], state.selectedKeys.single())
        }

        // since amount of arrow up is bigger than amount of items -> first element should be
        // selected
        assertTrue(state.selectedKeys.size == 1)
        assertEquals(items[0], state.selectedKeys.single())

        // press arrow down and check that selected key is changed
        repeat(40) { step ->
            composeRule.onNodeWithTag("list").performKeyInput { pressKey(Key.DirectionDown) }

            // check that next element is selected
            assertTrue(state.selectedKeys.size == 1)
            val expectedSelectedIndex = (step + 1).takeIf { it in items.indices } ?: items.lastIndex
            assertEquals(items[expectedSelectedIndex], state.selectedKeys.single())
        }

        // since amount of arrow down is bigger than amount of items -> last element should be
        // selected
        assertTrue(state.selectedKeys.size == 1)
        assertEquals(items.last(), state.selectedKeys.single())
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun `multiple items selection`() = runBlocking {
        val items = (0..10).toList()
        val state = SelectableLazyListState(LazyListState())
        composeRule.setContent {
            Box(modifier = Modifier.requiredHeight(300.dp)) {
                SelectableLazyColumn(state = state, modifier = Modifier.testTag("list")) {
                    items(items.size, key = { items[it] }) {
                        val itemText = "Item ${items[it]}"
                        BasicText(itemText, modifier = Modifier.testTag(itemText))
                    }
                }
            }
        }
        composeRule.awaitIdle()
        // select item 5 by click
        composeRule.onNodeWithTag("Item 5").assertExists()
        composeRule.onNodeWithTag("Item 5").performClick()

        // check that 5th element is selected
        assertEquals(1, state.selectedKeys.size)
        assertEquals(items[5], state.selectedKeys.single())

        // press arrow up with pressed Shift and check that selected keys are changed
        repeat(20) { step ->
            composeRule.onNodeWithTag("list").performKeyInput {
                withKeyDown(Key.ShiftLeft) { pressKey(Key.DirectionUp) }
            }

            // check that previous element is added to selection
            // when started from 5th element
            val expectedFirstSelectedIndex = (5 - step - 1).takeIf { it >= 0 } ?: 0
            val elements = items.subList(expectedFirstSelectedIndex, 6)
            assertEquals(elements.size, state.selectedKeys.size)
            assertEquals(elements.toSet(), state.selectedKeys.toSet())
        }

        // select first item by click
        composeRule.onNodeWithTag("Item 0").assertExists()
        composeRule.onNodeWithTag("Item 0").performClick()

        // check that first element is selected
        assertEquals(1, state.selectedKeys.size)
        assertEquals(items[0], state.selectedKeys.single())

        // press arrow down with pressed Shift and check that selected keys are changed
        repeat(20) { step ->
            composeRule.onNodeWithTag("list").performKeyInput {
                withKeyDown(Key.ShiftLeft) { pressKey(Key.DirectionDown) }
            }

            // check that next element is added to selection
            val expectedFirstSelectedIndex = (step + 1).takeIf { it in items.indices } ?: items.lastIndex
            val elements = items.subList(0, expectedFirstSelectedIndex + 1)
            assertEquals(elements.size, state.selectedKeys.size)
            assertEquals(elements.toSet(), state.selectedKeys.toSet())
        }

        // all elements should be selected in the end
        assertEquals(items.size, state.selectedKeys.size)
        assertEquals(items.toSet(), state.selectedKeys.toSet())
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun `select to first and last`() = runBlocking {
        val items = (0..50).toList()
        val state = SelectableLazyListState(LazyListState())
        composeRule.setContent {
            Box(modifier = Modifier.requiredHeight(300.dp)) {
                SelectableLazyColumn(state = state, modifier = Modifier.testTag("list")) {
                    items(items.size, key = { items[it] }) {
                        val itemText = "Item ${items[it]}"
                        BasicText(itemText, modifier = Modifier.testTag(itemText))
                    }
                }
            }
        }
        composeRule.awaitIdle()
        // select item 5 by click
        composeRule.onNodeWithTag("Item 5").assertExists()
        composeRule.onNodeWithTag("Item 5").performClick()

        // check that 5th element is selected
        assertEquals(1, state.selectedKeys.size)
        assertEquals(items[5], state.selectedKeys.single())

        // perform home with shift, so all items until 5th should be selected
        composeRule.onNodeWithTag("list").performKeyInput { withKeyDown(Key.ShiftLeft) { pressKey(Key.MoveHome) } }
        val expectedElementsAfterPageUp = items.subList(0, 6)
        assertEquals(expectedElementsAfterPageUp.size, state.selectedKeys.size)
        assertEquals(expectedElementsAfterPageUp.toSet(), state.selectedKeys.toSet())

        // select item 5 by click
        composeRule.onNodeWithTag("Item 5").assertExists()
        composeRule.onNodeWithTag("Item 5").performClick()

        // check that 5th element is selected
        assertEquals(1, state.selectedKeys.size)
        assertEquals(items[5], state.selectedKeys.single())

        // perform end with shift, so all items after 5th should be selected
        composeRule.onNodeWithTag("list").performKeyInput { withKeyDown(Key.ShiftLeft) { pressKey(Key.MoveEnd) } }
        val expectedElementsAfterPageDown = items.subList(5, items.lastIndex + 1)
        assertEquals(expectedElementsAfterPageDown.size, state.selectedKeys.size)
        assertEquals(expectedElementsAfterPageDown.toSet(), state.selectedKeys.toSet())
    }

    @Test
    fun `changing items model with selection shouldn't fail`() = runBlocking {
        val items1 = (0..50).toList()
        val items2 = (70..80).toList()
        val currentItems = mutableStateOf(items1)
        val state = SelectableLazyListState(LazyListState())
        composeRule.setContent {
            Box(modifier = Modifier.requiredHeight(300.dp)) {
                val items = currentItems.value
                SelectableLazyColumn(state = state, modifier = Modifier.testTag("list")) {
                    items(items.size, key = { items[it] }) {
                        val itemText = "Item ${items[it]}"
                        BasicText(itemText, modifier = Modifier.testTag(itemText))
                    }
                }
            }
        }
        composeRule.awaitIdle()
        // select item 5 by click
        composeRule.onNodeWithTag("Item 5").assertExists()
        composeRule.onNodeWithTag("Item 5").performClick()

        // check that 5th element is selected
        assertEquals(1, state.selectedKeys.size)
        assertEquals(currentItems.value[5], state.selectedKeys.single())

        // change items from 0..50 to 70..80, so "Item 5" doesn't exist
        currentItems.value = items2
        composeRule.awaitIdle()
        // TODO: should the selectedKeys be cleared when items are changed
        //  https://github.com/JetBrains/jewel/issues/242
        // assertEquals(0, state.selectedKeys.size)

        composeRule.onNodeWithTag("Item 75").assertExists()
        composeRule.onNodeWithTag("Item 75").performClick()

        assertEquals(1, state.selectedKeys.size)
        assertEquals(currentItems.value[5], state.selectedKeys.single())
    }
}
