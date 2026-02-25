// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jewel.ui.component.search

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.dp
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.jetbrains.jewel.foundation.util.JewelLogger
import org.jetbrains.jewel.intui.standalone.theme.IntUiTheme
import org.jetbrains.jewel.ui.component.DefaultButton
import org.jetbrains.jewel.ui.component.SimpleListItem
import org.jetbrains.jewel.ui.component.SpeedSearchArea
import org.jetbrains.jewel.ui.component.Text
import org.jetbrains.jewel.ui.component.assertCursorAtPosition
import org.jetbrains.jewel.ui.component.interactions.performKeyPress
import org.junit.Rule

@Suppress("ImplicitUnitReturnType")
@OptIn(ExperimentalCoroutinesApi::class)
class SpeedSearchableLazyColumnTest {
    @get:Rule val rule = createComposeRule()

    private val testDispatcher = UnconfinedTestDispatcher()

    private val ComposeContentTestRule.onLazyColumn
        get() = onNodeWithTag("LazyColumn")

    private val ComposeContentTestRule.onSpeedSearchAreaInput
        get() = onNodeWithTag("SpeedSearchArea.Input")

    private fun ComposeContentTestRule.onLazyColumnItem(text: String) =
        onNode(hasAnyAncestor(hasTestTag("LazyColumn")) and hasText(text))

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `should show on type text`() = runComposeTest {
        onLazyColumn.performKeyPress("Item 42", rule = this)
        onSpeedSearchAreaInput.assertExists().assertIsDisplayed()
    }

    @Test
    fun `should hide on esc press`() = runComposeTest {
        onLazyColumn.performKeyPress("Item 42", rule = this)
        onSpeedSearchAreaInput.assertExists().assertIsDisplayed()

        onLazyColumn.performKeyPress(Key.Escape, rule = this)
        onSpeedSearchAreaInput.assertDoesNotExist()
    }

    @Test
    fun `on option navigation, move cursor`() = runComposeTest {
        onLazyColumn.performKeyPress("Item 42", rule = this)
        onSpeedSearchAreaInput.assertExists().assertIsDisplayed()

        // Move to start
        onSpeedSearchAreaInput.performKeyPress(Key.DirectionLeft, alt = true, rule = this)
        onSpeedSearchAreaInput.assertCursorAtPosition(0)

        // Move to end
        onSpeedSearchAreaInput.performKeyPress(Key.DirectionRight, alt = true, rule = this)
        onSpeedSearchAreaInput.assertCursorAtPosition(7)
    }

    @Test
    fun `on type, select first occurrence`() = runComposeTest {
        onLazyColumn.performKeyPress("Item 2", rule = this)
        onLazyColumnItem("Item 2").assertIsDisplayed().assertIsSelected()
    }

    @Test
    fun `on type continue typing, continue selecting first occurrence`() = runComposeTest {
        onLazyColumn.performKeyPress("Item 2", rule = this)
        onLazyColumnItem("Item 2").assertIsDisplayed().assertIsSelected()

        onLazyColumn.performKeyPress("4", rule = this)
        onLazyColumnItem("Item 24").assertIsDisplayed().assertIsSelected()

        onLazyColumn.performKeyPress("5", rule = this)
        onLazyColumnItem("Item 245").assertIsDisplayed().assertIsSelected()
    }

    @Test
    fun `select first visible match if after the current item`() = runComposeTest {
        onLazyColumn.performKeyPress("Item 40", rule = this)
        onLazyColumnItem("Item 40").assertIsDisplayed().assertIsSelected()

        // Return scroll a bit and ensure 39 is visible
        onLazyColumn.performScrollToIndex(35)
        onLazyColumnItem("Item 39").assertIsDisplayed()

        // Delete the number and replace with "9"
        onSpeedSearchAreaInput.performKeyPress(Key.Backspace, rule = this)
        onSpeedSearchAreaInput.performKeyPress(Key.Backspace, rule = this)
        onSpeedSearchAreaInput.performKeyPress("9", rule = this)

        // 39 is the first visible item containing 9, so it will be selected
        onLazyColumnItem("Item 39").assertIsDisplayed().assertIsSelected()
    }

    @Test
    fun `select closest match if after the current item`() = runComposeTest {
        onLazyColumn.performKeyPress("Item 245", rule = this)
        onLazyColumnItem("Item 245").assertIsDisplayed().assertIsSelected()

        // Delete the number
        onSpeedSearchAreaInput.performKeyPress(Key.Backspace, rule = this)
        onSpeedSearchAreaInput.performKeyPress(Key.Backspace, rule = this)
        onSpeedSearchAreaInput.performKeyPress(Key.Backspace, rule = this)

        // Add `99` and jumps to next reference matching "99"
        onSpeedSearchAreaInput.performKeyPress("99", rule = this)
        onLazyColumnItem("Item 299").assertIsDisplayed().assertIsSelected()
    }

    @Test
    fun `on arrow up or down, navigate to the next and previous occurrences`() = runComposeTest {
        onLazyColumn.performKeyPress("Item 9", rule = this)
        onLazyColumnItem("Item 9").assertIsDisplayed().assertIsSelected()

        onLazyColumn.performKeyPress(Key.DirectionDown, rule = this)
        onLazyColumnItem("Item 19").assertIsDisplayed().assertIsSelected()

        onLazyColumn.performKeyPress(Key.DirectionDown, rule = this)
        onLazyColumnItem("Item 29").assertIsDisplayed().assertIsSelected()

        onLazyColumn.performKeyPress(Key.DirectionUp, rule = this)
        onLazyColumnItem("Item 19").assertIsDisplayed().assertIsSelected()

        onLazyColumn.performKeyPress(Key.DirectionUp, rule = this)
        onLazyColumnItem("Item 9").assertIsDisplayed().assertIsSelected()
    }

    @Test
    fun `deleting last char should keep current state`() = runComposeTest {
        onLazyColumn.performKeyPress("Item 42", rule = this)
        onSpeedSearchAreaInput.assertExists().assertIsDisplayed()
        onLazyColumnItem("Item 42").assertIsDisplayed().assertIsSelected()

        // Remove "2" from "Item 42" to make "Item 4", but keep 42 selected as it matches the search query
        onSpeedSearchAreaInput.performKeyPress(Key.Backspace, rule = this)
        onLazyColumnItem("Item 42").assertIsDisplayed().assertIsSelected()
    }

    @Test
    fun `should handle partial text matching`() = runComposeTest {
        onLazyColumn.performKeyPress("em 1", rule = this)
        onLazyColumnItem("Item 1").assertIsDisplayed().assertIsSelected()

        // Should match "Item 10", "Item 11", etc.
        onLazyColumn.performKeyPress(Key.DirectionDown, rule = this)
        onLazyColumnItem("Item 10").assertIsDisplayed().assertIsSelected()
    }

    @Test
    fun `should handle no matches gracefully`() = runComposeTest {
        // Select item 5
        onLazyColumnItem("Item 5").performClick().assertIsSelected()

        // Types something that does not match any item
        onLazyColumn.performKeyPress("Nope", rule = this)
        onSpeedSearchAreaInput.assertExists().assertIsDisplayed()

        // Selection should stay in the current item
        onLazyColumnItem("Item 5").assertIsSelected()
    }

    @Test
    fun `should handle special characters in search`() =
        runComposeTest(listEntries = listOf("Item-1", "Item.2", "Item@3", "Item#4", "Item$5")) {
            onLazyColumn.performKeyPress("Item-", rule = this)
            onLazyColumnItem("Item-1").assertIsDisplayed().assertIsSelected()
        }

    @Test
    fun `should keep text when navigating through matches`() = runComposeTest {
        onLazyColumn.performKeyPress("Item 2", rule = this)
        onLazyColumnItem("Item 2").assertIsDisplayed().assertIsSelected()

        // Navigate and check search is still active
        onLazyColumn.performKeyPress(Key.DirectionDown, rule = this)
        onLazyColumnItem("Item 12").assertIsDisplayed().assertIsSelected()
        onSpeedSearchAreaInput.assertExists().assertIsDisplayed()

        onLazyColumn.performKeyPress(Key.DirectionDown, rule = this)
        onLazyColumnItem("Item 20").assertIsDisplayed().assertIsSelected()
        onSpeedSearchAreaInput.assertExists().assertIsDisplayed()

        // Add more text to existing search
        onLazyColumn.performKeyPress("0", rule = this)
        onLazyColumnItem("Item 20").assertIsDisplayed().assertIsSelected()
    }

    @Test
    fun `on lose focus, hide input`() = runComposeTest {
        onLazyColumn.performKeyPress("Item 42", rule = this)
        onSpeedSearchAreaInput.assertExists().assertIsDisplayed()

        onNodeWithTag("Button").performClick()
        onSpeedSearchAreaInput.assertDoesNotExist()
    }

    @Test
    fun `on lose focus with dismissOnLoseFocus false, keep input visible`() =
        runComposeTest(dismissOnLoseFocus = false) {
            onLazyColumn.performKeyPress("Item 42", rule = this)
            onSpeedSearchAreaInput.assertExists().assertIsDisplayed()

            onNodeWithTag("Button").performClick()
            onSpeedSearchAreaInput.assertExists().assertIsDisplayed()
        }

    private fun runComposeTest(
        listEntries: List<String> = List(500) { "Item ${it + 1}" },
        dismissOnLoseFocus: Boolean = true,
        block: ComposeContentTestRule.() -> Unit,
    ) {
        rule.setContent {
            val focusRequester = remember { FocusRequester() }

            IntUiTheme {
                Column {
                    SpeedSearchArea(
                        modifier = Modifier.testTag("SpeedSearchArea"),
                        dismissOnLoseFocus = dismissOnLoseFocus,
                    ) {
                        SpeedSearchableLazyColumn(
                            modifier = Modifier.size(200.dp).testTag("LazyColumn").focusRequester(focusRequester),
                            dispatcher = testDispatcher,
                        ) {
                            items(listEntries, textContent = { it }) { item ->
                                var textLayoutResult by remember { mutableStateOf<TextLayoutResult?>(null) }

                                SimpleListItem(
                                    text = item.highlightTextSearch(),
                                    selected = isSelected,
                                    active = isActive,
                                    onTextLayout = { textLayoutResult = it },
                                    modifier =
                                        Modifier.fillMaxWidth().selectable(isSelected) {
                                            JewelLogger.getInstance("ChipsAndTree").info("Click on $item")
                                        },
                                    textModifier = Modifier.highlightSpeedSearchMatches(textLayoutResult),
                                )
                            }
                        }
                    }
                    DefaultButton(onClick = { println("Button Clicked") }, modifier = Modifier.testTag("Button")) {
                        Text("Press me")
                    }
                }
            }

            LaunchedEffect(Unit) { focusRequester.requestFocus() }
        }

        rule.block()
    }
}
