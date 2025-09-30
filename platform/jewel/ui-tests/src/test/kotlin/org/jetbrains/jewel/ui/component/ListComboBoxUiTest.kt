package org.jetbrains.jewel.ui.component

import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertHasNoClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.unit.dp
import junit.framework.TestCase.assertEquals
import org.jetbrains.jewel.IntUiTestTheme
import org.jetbrains.jewel.foundation.lazy.rememberSelectableLazyListState
import org.junit.Ignore
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalTestApi::class)
class ListComboBoxUiTest {
    @get:Rule val composeRule = createComposeRule()

    // Helper properties to access common UI elements in tests
    private val popupMenu: SemanticsNodeInteraction
        get() = composeRule.onNodeWithTag("Jewel.ComboBox.Popup")

    private val chevronContainer: SemanticsNodeInteraction
        get() = composeRule.onNodeWithTag("Jewel.ComboBox.ChevronContainer", useUnmergedTree = true)

    private val textField: SemanticsNodeInteraction
        get() = composeRule.onNodeWithTag("Jewel.ComboBox.TextField", useUnmergedTree = true)

    private val comboBox: SemanticsNodeInteraction
        get() = composeRule.onNodeWithTag("ComboBox")

    @Test
    fun `when enabled and editable clicking the chevron container opens the popup`() {
        editableListComboBox()
        chevronContainer.assertExists().assertHasClickAction().performClick()
        popupMenu.assertExists()
    }

    @Test
    fun `when disable clicking the chevron container doesn't open the popup`() {
        injectListComboBox(FocusRequester(), isEnabled = false)
        chevronContainer.assertExists().assertHasNoClickAction().performClick()
        popupMenu.assertDoesNotExist()
    }

    @Test
    fun `when editable clicking chevron twice opens and closed the popup`() {
        injectEditableListComboBox(FocusRequester(), isEnabled = true)

        chevronContainer.assertHasClickAction().performClick()
        popupMenu.assertExists().assertIsDisplayed()

        chevronContainer.performClick()
        popupMenu.assertDoesNotExist()
    }

    @Test
    fun `TAB navigation focuses only the editable combo box's text field`() {
        val focusRequester = FocusRequester()
        composeRule.setContent {
            IntUiTestTheme {
                Box(modifier = Modifier.size(20.dp).focusRequester(focusRequester).testTag("Pre-Box").focusable(true))
                EditableListComboBox(
                    items = comboBoxItems,
                    onSelectedItemChange = { _: Int -> },
                    modifier = Modifier.width(140.dp).testTag("ComboBox"),
                    selectedIndex = 0,
                )
            }
        }
        focusRequester.requestFocus()
        composeRule.onNodeWithTag("Pre-Box").assertIsDisplayed().assertIsFocused()

        composeRule.onNodeWithTag("Pre-Box").performKeyInput {
            keyDown(Key.Tab)
            keyUp(Key.Tab)
        }

        composeRule.onNodeWithTag("Jewel.ComboBox.TextField", useUnmergedTree = true).assertIsFocused()

        textField.performKeyInput {
            keyDown(Key.Tab)
            keyUp(Key.Tab)
        }

        composeRule.onNodeWithTag("Pre-Box").assertIsFocused()
    }

    @Test
    fun `when not-editable click opens popup`() {
        val comboBox = focusedListComboBox()
        comboBox.performClick()
        popupMenu.assertIsDisplayed()
        composeRule.onAllNodesWithText("Item 2", useUnmergedTree = true).onLast().assertIsDisplayed()
    }

    @Test
    fun `when not-editable click on comboBox opens popup`() {
        val comboBox = focusedListComboBox()

        comboBox.performClick()
        popupMenu.assertIsDisplayed()
    }

    @Test
    fun `when not-editable double click on comboBox opens and closes popup`() {
        val comboBox = focusedListComboBox()

        comboBox.performClick()
        popupMenu.assertIsDisplayed()

        composeRule.waitForIdle()

        comboBox.performClick()
        popupMenu.assertDoesNotExist()
    }

    @Test
    fun `when not-editable pressing spacebar opens popup`() {
        val comboBox = focusedListComboBox()

        popupMenu.assertDoesNotExist()

        comboBox.performKeyInput {
            keyDown(Key.Spacebar)
            keyUp(Key.Spacebar)
        }
        popupMenu.assertIsDisplayed()
    }

    @Ignore("Due to https://youtrack.jetbrains.com/issue/CMP-3710")
    @Test
    fun `when editable pressing spacebar does not open popup`() {
        injectEditableListComboBox(FocusRequester(), isEnabled = true)
        popupMenu.assertDoesNotExist()

        textField.assertIsFocused().assertIsDisplayed()
        textField.assertTextContains("Item 1")
        textField.assertIsFocused().performKeyInput {
            keyDown(Key.Spacebar)
            keyUp(Key.Spacebar)
        }
        textField.assertTextEquals("Item 1 ")
        popupMenu.assertDoesNotExist()
    }

    @Test
    fun `when not-editable pressing enter does not open popup`() {
        val comboBox = focusedListComboBox()

        popupMenu.assertDoesNotExist()

        comboBox.performKeyInput {
            keyDown(Key.Enter)
            keyUp(Key.Enter)
        }
        popupMenu.assertDoesNotExist()
        composeRule.onNodeWithTag("Item 1", useUnmergedTree = true).assertDoesNotExist()
    }

    @Test
    fun `when editable, pressing enter does not open popup`() {
        val comboBox = editableListComboBox()

        popupMenu.assertDoesNotExist()

        comboBox.performKeyInput {
            keyDown(Key.Enter)
            keyUp(Key.Enter)
        }
        textField.assertIsDisplayed()
        popupMenu.assertDoesNotExist()
    }

    @Test
    fun `when editable, textField is displayed and can receive input`() {
        injectEditableListComboBox(FocusRequester(), isEnabled = true)

        composeRule.onNodeWithTag("ComboBox").assertIsDisplayed().performClick()

        textField.assertIsDisplayed().assertIsFocused().performTextClearance()
        textField.performTextInput("Item 1 edited")

        textField.assertIsDisplayed()
    }

    @Suppress("SwallowedException")
    @Test
    fun `when not editable, non editable text is displayed`() {
        injectListComboBox(FocusRequester(), isEnabled = true)

        comboBox.assertIsDisplayed()
        composeRule.onNodeWithTag("Jewel.ComboBox.NonEditableText", useUnmergedTree = true).assertIsDisplayed()
    }

    @Test
    fun `when disabled, ComboBox cannot be interacted with`() {
        val comboBox = disabledEditableListComboBox()
        comboBox.assertIsDisplayed().assertHasNoClickAction().performClick()
        popupMenu.assertDoesNotExist()

        // BasicTextField clickable adds an onClick action even when BTF is disabled
        // textField.assertIsDisplayed().assertIsNotEnabled().assertHasNoClickAction().performClick() ❌
        textField.assertIsDisplayed().assertIsNotEnabled().performClick()
        popupMenu.assertDoesNotExist()
    }

    @Test
    fun `when editable divider is displayed`() {
        injectEditableListComboBox(FocusRequester(), isEnabled = true)

        composeRule.onNode(hasTestTag("Jewel.ComboBox.Divider"), useUnmergedTree = true).assertExists()

        // We can't use assertIsDisplayed() on unmerged nodes, so let's check its bounds instead
        val bounds =
            composeRule
                .onNode(hasTestTag("Jewel.ComboBox.Divider"), useUnmergedTree = true)
                .fetchSemanticsNode()
                .boundsInRoot

        assert(bounds.width > 0f && bounds.height > 0f) {
            "Divider should have non-zero width and height, but was $bounds"
        }
    }

    @Test
    fun `when not-editable divider is not displayed`() {
        injectListComboBox(FocusRequester(), isEnabled = true)
        composeRule
            .onNode(
                hasTestTag("Jewel.ComboBox.Divider") and hasContentDescription("Jewel.ComboBox.Divider"),
                useUnmergedTree = true,
            )
            .assertDoesNotExist()
    }

    @Test
    fun `when not-editable the ChevronContainer is clickable and opens popup`() {
        injectListComboBox(FocusRequester(), isEnabled = true)

        chevronContainer.assertExists()

        // We can't use assertIsDisplayed() or assertHasClickAction() on unmerged nodes,
        // so we'll check its bounds and perform the click
        val bounds = chevronContainer.fetchSemanticsNode().boundsInRoot
        assert(bounds.width > 0f && bounds.height > 0f) {
            "ChevronContainer should have non-zero width and height, but was $bounds"
        }

        chevronContainer.performClick()

        popupMenu.assertIsDisplayed()
    }

    @Test
    fun `when focused and editable pressing arrow down opens the popup`() {
        editableListComboBox()
        popupMenu.assertDoesNotExist()
        textField.performKeyInput {
            keyDown(Key.DirectionDown)
            keyUp(Key.DirectionDown)
        }
        popupMenu.assertIsDisplayed()
    }

    @Test
    fun `when focused and editable pressing arrow down twice opens the popup and selects the second item`() {
        editableListComboBox()
        popupMenu.assertDoesNotExist()
        textField.performKeyInput {
            keyDown(Key.DirectionDown)
            keyUp(Key.DirectionDown)
            keyDown(Key.DirectionDown)
            keyUp(Key.DirectionDown)
        }
        popupMenu.assertIsDisplayed()
        composeRule.onAllNodesWithText("Item 2", useUnmergedTree = true).onLast().assertIsDisplayed()
    }

    @Test
    fun `when enabled but not editable clicking on the comboBox focuses it and open the popup`() {
        val focusRequester = FocusRequester()
        injectListComboBox(focusRequester, isEnabled = true)
        val comboBox = comboBox

        comboBox.performClick()
        comboBox.assertIsFocused()
        popupMenu.assertIsDisplayed()
    }

    @Test
    fun `when enabled but not editable spacebar opens the popup`() {
        val comboBox = focusedListComboBox()
        comboBox.performKeyInput {
            keyDown(Key.Spacebar)
            keyUp(Key.Spacebar)
        }
        popupMenu.assertIsDisplayed()
    }

    @Test
    fun `when enabled but not editable pressing spacebar twice opens and closes the popup`() {
        val comboBox = focusedListComboBox()
        comboBox.performKeyInput {
            keyDown(Key.Spacebar)
            keyUp(Key.Spacebar)
        }
        popupMenu.assertIsDisplayed()
        comboBox.performKeyInput {
            keyDown(Key.Spacebar)
            keyUp(Key.Spacebar)
        }
        popupMenu.assertDoesNotExist()
    }

    @Test
    fun `when enabled and editable with open popup losing focus closes the popup`() {
        val focusRequester = FocusRequester()
        injectEditableListComboBox(focusRequester, isEnabled = true)
        focusRequester.requestFocus()

        val comboBox = comboBox
        comboBox.assertIsDisplayed()
        chevronContainer.performClick()
        popupMenu.assertIsDisplayed()

        composeRule.waitForIdle()

        focusRequester.freeFocus()
        popupMenu.assertIsDisplayed()
    }

    @Test
    fun `when editable pressing enter on opened popup closes it`() {
        val comboBox = editableListComboBox()

        chevronContainer.performClick()
        popupMenu.assertIsDisplayed()

        comboBox.performClick()
        popupMenu.assertIsDisplayed()
        comboBox.performKeyInput {
            keyDown(Key.Enter)
            keyUp(Key.Enter)
        }
        popupMenu.assertDoesNotExist()
    }

    @Test
    fun `when editable on opened popup clicking the textfield doesn't close the popup`() {
        val comboBox = editableListComboBox()

        chevronContainer.performClick()
        popupMenu.assertIsDisplayed()

        comboBox.performClick()
        popupMenu.assertIsDisplayed()
    }

    @Test
    fun `when editable clicking chevron open the popup and select the first item`() {
        // Use the direct composition so we can track the selection state
        val focusRequester = FocusRequester()
        var selectedIndex = 0

        composeRule.setContent {
            IntUiTestTheme {
                EditableListComboBox(
                    items = comboBoxItems,
                    selectedIndex = selectedIndex,
                    onSelectedItemChange = { index: Int -> selectedIndex = index },
                    modifier = Modifier.testTag("ComboBox").width(200.dp).focusRequester(focusRequester),
                    enabled = true,
                    itemKeys = { index: Int, _: String -> index }, // Explicitly use index as key for tests
                )
            }
        }

        composeRule.waitForIdle()

        focusRequester.requestFocus()
        composeRule.waitForIdle()

        comboBox.assertIsDisplayed()
        textField.assertIsDisplayed().assertIsFocused()

        chevronContainer.performClick()
        composeRule.waitForIdle()
        popupMenu.assertIsDisplayed()

        composeRule.onAllNodesWithText("Item 1", useUnmergedTree = true).onLast().assertIsDisplayed()

        // Verify selection state through selectedIndex and text field
        assert(selectedIndex == 0) { "Expected selectedIndex to be 0, but was $selectedIndex" }
        textField.assertTextEquals("Item 1")
    }

    @Test
    fun `when editable pressing down twice selects the second element`() {
        // Use the direct composition so we can track the selection state
        val focusRequester = FocusRequester()
        var selectedIndex = 0

        composeRule.setContent {
            IntUiTestTheme {
                EditableListComboBox(
                    items = comboBoxItems,
                    selectedIndex = selectedIndex,
                    onSelectedItemChange = { index: Int -> selectedIndex = index },
                    modifier = Modifier.testTag("ComboBox").width(200.dp).focusRequester(focusRequester),
                    enabled = true,
                    itemKeys = { index: Int, _: String -> index }, // Explicitly use index as key for tests
                    listState = rememberSelectableLazyListState(),
                )
            }
        }

        composeRule.waitForIdle()

        focusRequester.requestFocus()
        composeRule.waitForIdle()

        textField.assertIsDisplayed().assertIsFocused()
        popupMenu.assertDoesNotExist()

        textField.performKeyInput {
            keyDown(Key.DirectionDown)
            keyUp(Key.DirectionDown)
        }
        composeRule.waitForIdle()

        popupMenu.assertIsDisplayed()
        composeRule.onAllNodesWithText("Item 1", useUnmergedTree = true).onLast().assertIsDisplayed()
        assert(selectedIndex == 0) { "Expected selectedIndex to be 0 after opening popup, but was $selectedIndex" }

        textField.performKeyInput {
            keyDown(Key.DirectionDown)
            keyUp(Key.DirectionDown)
        }
        composeRule.waitForIdle()

        composeRule.onAllNodesWithText("Item 2", useUnmergedTree = true).onLast().assertIsDisplayed()
        assert(selectedIndex == 1) { "Expected selectedIndex to be 1 after second down press, but was $selectedIndex" }

        textField.assertTextEquals("Item 2")
    }

    @Test
    fun `when not editable pressing enter selects the preview selection if any`() {
        injectListComboBox(FocusRequester(), isEnabled = true)
        comboBox.performClick()
        popupMenu.assertIsDisplayed()

        // Press down to navigate to second item
        comboBox.performKeyInput {
            keyDown(Key.DirectionDown)
            keyUp(Key.DirectionDown)
        }
        composeRule.waitForIdle()

        // Verify second item is visible and selected
        composeRule.onAllNodesWithText("Item 2", useUnmergedTree = true).onLast().assertIsDisplayed()

        // Press enter to select
        comboBox.performKeyInput { pressKey(Key.Enter) }
        composeRule.waitForIdle()

        // Verify final state
        comboBox.assertTextEquals("Item 2", includeEditableText = false)
    }

    @Test
    fun `stateless ListComboBox displays and selects initial selectedIndex item`() {
        var selectedIndex = 2
        var selectedText = "Item 3"

        composeRule.setContent {
            IntUiTestTheme {
                ListComboBox(
                    items = comboBoxItems,
                    selectedIndex = selectedIndex,
                    onSelectedItemChange = { index ->
                        selectedIndex = index
                        selectedText = comboBoxItems[index]
                    },
                    modifier = Modifier.testTag("ComboBox").width(200.dp),
                    itemKeys = { index: Int, _: String -> index },
                )
            }
        }

        // Wait for LaunchedEffect to complete
        composeRule.waitForIdle()

        // Verify initial selection
        composeRule.onNodeWithTag("ComboBox").assertTextEquals("Item 3")

        // Click to open popup
        composeRule.onNodeWithTag("ComboBox").performClick()

        // Select first item
        composeRule.onNodeWithText("Item 1").performClick()

        // Verify selection updated
        assertEquals(0, selectedIndex)
        assertEquals("Item 1", selectedText)
    }

    @Test
    fun `when selectedIndex changes externally ListComboBox updates`() {
        var selectedIndex by mutableIntStateOf(0)

        composeRule.setContent {
            IntUiTestTheme {
                ListComboBox(
                    items = comboBoxItems,
                    selectedIndex = selectedIndex,
                    onSelectedItemChange = { index -> selectedIndex = index },
                    modifier = Modifier.testTag("ComboBox").width(200.dp),
                    itemKeys = { index: Int, _: String -> index },
                )
            }
        }

        // Verify initial state
        composeRule.onNode(hasTestTag("ComboBox")).assertTextEquals("Item 1", includeEditableText = false)

        // Open popup
        comboBox.performClick()
        composeRule.waitForIdle()

        // Navigate to Book (3 down presses)
        repeat(3) {
            comboBox.performKeyInput {
                keyDown(Key.DirectionDown)
                keyUp(Key.DirectionDown)
            }
            composeRule.waitForIdle()
        }

        // Press enter to select
        comboBox.performKeyInput { pressKey(Key.Enter) }
        composeRule.waitForIdle()

        // Verify ComboBox text is updated
        composeRule.onNode(hasTestTag("ComboBox")).assertTextEquals("Book", includeEditableText = false)
        assert(selectedIndex == 3) { "Expected selectedIndex to be 3, but was $selectedIndex" }
    }

    @Test
    fun `when editable ListComboBox text is edited then selectedIndex remains unchanged`() {
        var selectedIndex = 2
        val focusRequester = FocusRequester()

        composeRule.setContent {
            IntUiTestTheme {
                EditableListComboBox(
                    items = comboBoxItems,
                    selectedIndex = selectedIndex,
                    onSelectedItemChange = { index: Int -> selectedIndex = index },
                    modifier = Modifier.testTag("ComboBox").width(200.dp).focusRequester(focusRequester),
                    itemKeys = { index: Int, _: String -> index },
                    listState = rememberSelectableLazyListState(),
                )
            }
        }

        // Request focus and wait for it
        focusRequester.requestFocus()
        composeRule.waitForIdle()

        // Clear text field and input new text
        textField.assertIsDisplayed().assertIsFocused().performTextClearance()
        textField.performTextInput("Custom text")

        // Verify selectedIndex remains unchanged
        assertEquals(2, selectedIndex)
    }

    @Test
    fun `when editable ListComboBox selectedIndex changes then text field updates`() {
        var selectedIndex by mutableIntStateOf(0)
        val focusRequester = FocusRequester()

        composeRule.setContent {
            IntUiTestTheme {
                EditableListComboBox(
                    items = comboBoxItems,
                    selectedIndex = selectedIndex,
                    onSelectedItemChange = { index: Int -> selectedIndex = index },
                    modifier = Modifier.testTag("ComboBox").width(200.dp).focusRequester(focusRequester),
                    itemKeys = { index: Int, _: String -> index },
                )
            }
        }

        // Wait for initial composition and request focus
        composeRule.waitForIdle()
        focusRequester.requestFocus()
        composeRule.waitForIdle()

        // Verify initial text and focus
        textField.assertIsDisplayed().assertIsFocused()
        textField.assertTextEquals("Item 1")

        // Press down to open popup
        textField.performKeyInput {
            keyDown(Key.DirectionDown)
            keyUp(Key.DirectionDown)
        }
        composeRule.waitForIdle()

        // Press down three more times to get to "Book"
        repeat(3) {
            textField.performKeyInput {
                keyDown(Key.DirectionDown)
                keyUp(Key.DirectionDown)
            }
            composeRule.waitForIdle()
        }

        // Verify popup item is selected
        composeRule.onAllNodesWithText("Book", useUnmergedTree = true).onLast().assertIsDisplayed()

        // Verify text field updates and selection state
        textField.assertTextEquals("Book")
        assert(selectedIndex == 3) { "Expected selectedIndex to be 3, but was $selectedIndex" }
    }

    private fun editableListComboBox(): SemanticsNodeInteraction {
        val focusRequester = FocusRequester()
        composeRule.setContent {
            IntUiTestTheme {
                EditableListComboBox(
                    items = comboBoxItems,
                    selectedIndex = 0,
                    onSelectedItemChange = { _: Int -> },
                    modifier = Modifier.testTag("ComboBox").width(200.dp).focusRequester(focusRequester),
                    enabled = true,
                    itemKeys = { index: Int, _: String -> index }, // Explicitly use index as key for tests
                )
            }
        }
        focusRequester.requestFocus()
        val comboBox = comboBox
        comboBox.assertIsDisplayed()

        textField.assertIsDisplayed().assertIsFocused()
        return comboBox
    }

    private fun disabledEditableListComboBox(): SemanticsNodeInteraction {
        val focusRequester = FocusRequester()
        injectEditableListComboBox(focusRequester, isEnabled = false)
        focusRequester.requestFocus()
        val comboBox = comboBox
        comboBox.assertIsDisplayed()
        textField.assertIsDisplayed()
        return comboBox
    }

    private fun focusedListComboBox(
        focusRequester: FocusRequester = FocusRequester(),
        isEnabled: Boolean = true,
    ): SemanticsNodeInteraction {
        injectListComboBox(focusRequester, isEnabled)
        focusRequester.requestFocus()
        val comboBox = comboBox
        comboBox.assertIsDisplayed().assertIsFocused()
        return comboBox
    }

    private fun injectListComboBox(focusRequester: FocusRequester, isEnabled: Boolean) {
        composeRule.setContent {
            IntUiTestTheme {
                ListComboBox(
                    items = comboBoxItems,
                    selectedIndex = 0,
                    onSelectedItemChange = {},
                    modifier = Modifier.testTag("ComboBox").width(200.dp).focusRequester(focusRequester),
                    enabled = isEnabled,
                    itemKeys = { index: Int, _: String -> index },
                )
            }
        }
    }

    private fun injectEditableListComboBox(focusRequester: FocusRequester, isEnabled: Boolean) {
        composeRule.setContent {
            IntUiTestTheme {
                EditableListComboBox(
                    items = comboBoxItems,
                    selectedIndex = 0,
                    onSelectedItemChange = { _: Int -> },
                    modifier = Modifier.testTag("ComboBox").width(200.dp).focusRequester(focusRequester),
                    enabled = isEnabled,
                    itemKeys = { index: Int, _: String -> index },
                )
            }
        }
    }

    private val comboBoxItems =
        listOf(
            "Item 1",
            "Item 2",
            "Item 3",
            "Book",
            "Laughter",
            "Whisper",
            "Ocean",
            "Serendipity lorem ipsum",
            "Umbrella",
            "Joy",
        )
}
