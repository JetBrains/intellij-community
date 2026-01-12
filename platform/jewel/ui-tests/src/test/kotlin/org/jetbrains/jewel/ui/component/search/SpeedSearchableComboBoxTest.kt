// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jewel.ui.component.search

import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.jetbrains.jewel.intui.standalone.theme.IntUiTheme
import org.jetbrains.jewel.ui.component.SpeedSearchArea
import org.jetbrains.jewel.ui.component.assertCursorAtPosition
import org.jetbrains.jewel.ui.component.interactions.performKeyPress
import org.junit.Rule

@Suppress("ImplicitUnitReturnType")
@OptIn(ExperimentalCoroutinesApi::class)
class SpeedSearchableComboBoxTest {
    @get:Rule val rule = createComposeRule()

    private val testDispatcher = UnconfinedTestDispatcher()

    private val comboBox: SemanticsNodeInteraction
        get() = rule.onNodeWithTag("ComboBox")

    private val ComposeContentTestRule.onSpeedSearchAreaInput
        get() = onNodeWithTag("SpeedSearchArea.Input")

    private fun ComposeContentTestRule.onComboBoxItem(text: String) =
        onNode(hasAnyAncestor(hasTestTag("Jewel.ComboBox.List")) and hasText(text))

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
        comboBox.performClick()
        comboBox.performKeyPress("Item", rule = this)
        onSpeedSearchAreaInput.assertExists().assertIsDisplayed()
    }

    @Test
    fun `should not show on type text before opening the popup`() = runComposeTest {
        comboBox.performKeyPress("Item", rule = this)
        onSpeedSearchAreaInput.assertDoesNotExist()
    }

    @Test
    fun `should hide on esc press`() = runComposeTest {
        comboBox.performClick()

        comboBox.performKeyPress("Item", rule = this)
        onSpeedSearchAreaInput.assertExists().assertIsDisplayed()

        comboBox.performKeyPress(Key.Escape, rule = this)
        onSpeedSearchAreaInput.assertDoesNotExist()
    }

    @Test
    fun `on option navigation, move input cursor`() = runComposeTest {
        comboBox.performClick()

        comboBox.performKeyPress("Item 42", rule = this)

        comboBox.performKeyPress(Key.DirectionLeft, alt = true, rule = this)
        onSpeedSearchAreaInput.assertCursorAtPosition(0)

        comboBox.performKeyPress(Key.DirectionRight, alt = true, rule = this)
        onSpeedSearchAreaInput.assertCursorAtPosition(7)
    }

    @Test
    fun `on type, select first occurrence`() = runComposeTest {
        comboBox.performClick()
        comboBox.performKeyPress("Item 2", rule = this)
        onComboBoxItem("Item 2").assertIsDisplayed().assertIsSelected()
    }

    @Test
    fun `on type continue typing, continue selecting first occurrence`() = runComposeTest {
        comboBox.performClick()

        comboBox.performKeyPress("Item 2", rule = this)
        onComboBoxItem("Item 2").assertIsDisplayed().assertIsSelected()

        onSpeedSearchAreaInput.performKeyPress("4", rule = this)
        onComboBoxItem("Item 24").assertIsDisplayed().assertIsSelected()

        onSpeedSearchAreaInput.performKeyPress("5", rule = this)
        onComboBoxItem("Item 245").assertIsDisplayed().assertIsSelected()
    }

    @Test
    fun `select closest match if after the current item`() = runComposeTest {
        comboBox.performClick()
        comboBox.performKeyPress("Item 245", rule = this)
        onComboBoxItem("Item 245").assertIsDisplayed().assertIsSelected()

        // Delete the number
        onSpeedSearchAreaInput.performKeyPress(Key.Backspace, rule = this)
        onSpeedSearchAreaInput.performKeyPress(Key.Backspace, rule = this)
        onSpeedSearchAreaInput.performKeyPress(Key.Backspace, rule = this)

        // Add `99` and jumps to next reference matching "99"
        onSpeedSearchAreaInput.performKeyPress("99", rule = this)
        onComboBoxItem("Item 299").assertIsDisplayed().assertIsSelected()
    }

    @Test
    fun `on arrow up or down, navigate to the next and previous occurrences`() = runComposeTest {
        comboBox.performClick()
        comboBox.performKeyPress("1", rule = this)
        onComboBoxItem("Item 1").assertIsDisplayed().assertIsSelected()

        comboBox.performKeyPress(Key.DirectionDown, rule = this)
        onComboBoxItem("Item 10").assertIsDisplayed().assertIsSelected()

        comboBox.performKeyPress(Key.DirectionDown, rule = this)
        onComboBoxItem("Item 11").assertIsDisplayed().assertIsSelected()

        comboBox.performKeyPress(Key.DirectionUp, rule = this)
        onComboBoxItem("Item 10").assertIsDisplayed().assertIsSelected()

        comboBox.performKeyPress(Key.DirectionUp, rule = this)
        onComboBoxItem("Item 1").assertIsDisplayed().assertIsSelected()
    }

    @Test
    fun `deleting last char should keep current state`() = runComposeTest {
        comboBox.performClick()
        comboBox.performKeyPress("Item 42", rule = this)
        onSpeedSearchAreaInput.assertExists().assertIsDisplayed()
        onComboBoxItem("Item 42").assertIsDisplayed().assertIsSelected()

        // Remove "2" from "Item 42" to make "Item 4", but keep 42 selected as it matches the search query
        onSpeedSearchAreaInput.performKeyPress(Key.Backspace, rule = this)
        onComboBoxItem("Item 42").assertIsDisplayed().assertIsSelected()
    }

    @Test
    fun `should handle partial text matching`() = runComposeTest {
        comboBox.performClick()

        comboBox.performKeyPress("em 1", rule = this)
        onComboBoxItem("Item 1").assertIsDisplayed().assertIsSelected()
    }

    @Test
    fun `should keep text when navigating through matches`() = runComposeTest {
        comboBox.performClick()
        comboBox.performKeyPress("Item 1", rule = this)
        onComboBoxItem("Item 1").assertIsDisplayed().assertIsSelected()

        comboBox.performKeyPress(Key.DirectionDown, rule = this)
        onComboBoxItem("Item 10").assertIsDisplayed().assertIsSelected()
        onSpeedSearchAreaInput.assertExists().assertIsDisplayed()

        comboBox.performKeyPress(Key.DirectionDown, rule = this)
        onComboBoxItem("Item 11").assertIsDisplayed().assertIsSelected()
        onSpeedSearchAreaInput.assertExists().assertIsDisplayed()

        comboBox.performKeyPress(Key.DirectionUp, rule = this)
        onComboBoxItem("Item 10").assertIsDisplayed().assertIsSelected()
        onSpeedSearchAreaInput.assertExists().assertIsDisplayed()

        comboBox.performKeyPress(Key.DirectionUp, rule = this)
        onComboBoxItem("Item 1").assertIsDisplayed().assertIsSelected()
        onSpeedSearchAreaInput.assertExists().assertIsDisplayed()
    }

    private fun runComposeTest(
        entries: List<String> = List(500) { "Item ${it + 1}" },
        block: ComposeContentTestRule.() -> Unit,
    ) {
        rule.setContent {
            val focusRequester = remember { FocusRequester() }

            IntUiTheme {
                var selectedIndex by remember { mutableIntStateOf(0) }
                SpeedSearchArea(
                    modifier = Modifier.widthIn(max = 200.dp).focusRequester(focusRequester).testTag("SpeedSearchArea")
                ) {
                    SpeedSearchableComboBox(
                        items = entries,
                        selectedIndex = selectedIndex,
                        onSelectedItemChange = { index -> selectedIndex = index },
                        modifier = Modifier.testTag("ComboBox"),
                        dispatcher = testDispatcher,
                    )
                }
            }

            LaunchedEffect(Unit) { focusRequester.requestFocus() }
        }

        rule.block()
    }
}
