package org.jetbrains.jewel.ui.component

import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
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
import org.jetbrains.jewel.foundation.lazy.rememberSelectableLazyListState
import org.jetbrains.jewel.intui.standalone.theme.IntUiTheme
import org.junit.Ignore
import org.junit.Rule
import org.junit.Test

/**
 * UI Tests for the ListComboBox and EditableListComboBox components. These tests verify the behavior of both editable
 * and non-editable combo boxes in various interaction scenarios including:
 * - Focus management
 * - Keyboard navigation
 * - Mouse interaction
 * - Popup menu behavior
 * - Text input (for editable combo boxes)
 * - State management
 */
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

    /** Tests that clicking the chevron container opens the popup menu when the combo box is enabled and editable. */
    @Test
    fun `when enabled and editable clicking the chevron container opens the popup`() {
        editableListComboBox()
        chevronContainer.assertExists().assertHasClickAction().performClick()
        popupMenu.assertExists()
    }

    /** Verifies that clicking the chevron container has no effect when the combo box is disabled. */
    @Test
    fun `when disable clicking the chevron container doesn't open the popup`() {
        injectListComboBox(FocusRequester(), isEnabled = false)
        chevronContainer.assertExists().assertHasNoClickAction().performClick()
        popupMenu.assertDoesNotExist()
    }

    /** Tests that clicking the chevron twice toggles the popup menu visibility in an editable combo box. */
    @Test
    fun `when editable clicking chevron twice opens and closed the popup`() {
        injectEditableListComboBox(FocusRequester(), isEnabled = true)

        chevronContainer.assertHasClickAction().performClick()
        popupMenu.assertExists().assertIsDisplayed()

        chevronContainer.performClick()
        popupMenu.assertDoesNotExist()
    }

    /**
     * Verifies proper TAB key navigation behavior in editable combo boxes. The test ensures that only the text field
     * receives focus when tabbing through.
     */
    @Test
    fun `TAB navigation focuses only the editable combo box's text field`() {
        val focusRequester = FocusRequester()
        composeRule.setContent {
            IntUiTheme {
                Box(modifier = Modifier.size(20.dp).focusRequester(focusRequester).testTag("Pre-Box").focusable(true))
                EditableListComboBox(
                    items = comboBoxItems,
                    initialSelectedIndex = 0,
                    onSelectedItemChange = { _: Int, _: String -> },
                    modifier = Modifier.width(140.dp).testTag("ComboBox"),
                    isEnabled = true,
                    listState = rememberSelectableLazyListState(),
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

    /** Tests that clicking on a non-editable combo box opens the popup menu. */
    @Test
    fun `when not-editable click opens popup`() {
        val comboBox = focusedListComboBox()
        comboBox.performClick()
        popupMenu.assertIsDisplayed()
        composeRule.onAllNodesWithText("Item 2", useUnmergedTree = true).onLast().assertIsDisplayed()
    }

    /** Verifies that clicking on the combo box area opens the popup menu in a non-editable combo box. */
    @Test
    fun `when not-editable click on comboBox opens popup`() {
        val comboBox = focusedListComboBox()

        comboBox.performClick()
        popupMenu.assertIsDisplayed()
    }

    /** Tests that double-clicking on a non-editable combo box toggles the popup menu visibility (open then close). */
    @Test
    fun `when not-editable double click on comboBox opens and closes popup`() {
        val comboBox = focusedListComboBox()

        comboBox.performClick()
        popupMenu.assertIsDisplayed()

        composeRule.waitForIdle()

        comboBox.performClick()
        popupMenu.assertDoesNotExist()
    }

    /** Verifies that pressing the spacebar key opens the popup menu in a non-editable combo box. */
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

    /**
     * Tests that pressing spacebar in an editable combo box types a space character instead of opening the popup menu.
     * Currently ignored due to a known issue tracked in YouTrack.
     */
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

    /** Verifies that pressing enter does not open the popup menu in a non-editable combo box. */
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

    /** Tests that pressing enter in an editable combo box does not open the popup menu. */
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

    /** Verifies that the text field in an editable combo box is properly displayed and can receive text input. */
    @Test
    fun `when editable, textField is displayed and can receive input`() {
        injectEditableListComboBox(FocusRequester(), isEnabled = true)

        composeRule.onNodeWithTag("ComboBox").assertIsDisplayed().performClick()

        textField.assertIsDisplayed().assertIsFocused().performTextClearance()
        textField.performTextInput("Item 1 edited")

        textField.assertIsDisplayed()
    }

    /** Tests that a non-editable combo box displays non-editable text instead of a text field. */
    @Suppress("SwallowedException")
    @Test
    fun `when not editable, non editable text is displayed`() {
        injectListComboBox(FocusRequester(), isEnabled = true)

        comboBox.assertIsDisplayed()
        composeRule.onNodeWithTag("Jewel.ComboBox.NonEditableText", useUnmergedTree = true).assertIsDisplayed()
    }

    /** Verifies that a disabled combo box cannot be interacted with, preventing both clicks and text input. */
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

    /** Tests that the divider is properly displayed in an editable combo box. */
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

    /** Verifies that the divider is not displayed in a non-editable combo box. */
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

    /**
     * Tests that the chevron container in a non-editable combo box is clickable and opens the popup menu when clicked.
     */
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

    /** Verifies that pressing the down arrow key opens the popup menu in an editable combo box. */
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

    /**
     * Tests that pressing the down arrow key twice in an editable combo box opens the popup and selects the second
     * item.
     */
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

    /** Verifies that clicking on a non-editable combo box both focuses it and opens the popup menu. */
    @Test
    fun `when enabled but not editable clicking on the comboBox focuses it and open the popup`() {
        val focusRequester = FocusRequester()
        injectListComboBox(focusRequester, isEnabled = true)
        val comboBox = comboBox

        comboBox.performClick()
        comboBox.assertIsFocused()
        popupMenu.assertIsDisplayed()
    }

    /** Tests that pressing spacebar opens the popup menu in a non-editable combo box. */
    @Test
    fun `when enabled but not editable spacebar opens the popup`() {
        val comboBox = focusedListComboBox()
        comboBox.performKeyInput {
            keyDown(Key.Spacebar)
            keyUp(Key.Spacebar)
        }
        popupMenu.assertIsDisplayed()
    }

    /** Verifies that pressing spacebar twice toggles the popup menu visibility in a non-editable combo box. */
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

    /**
     * Tests that losing focus in an editable combo box with an open popup keeps the popup open, allowing for
     * interaction with popup items.
     */
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

    /** Verifies that pressing enter while the popup is open in an editable combo box closes the popup. */
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

    /** Tests that clicking the text field while the popup is open keeps the popup open in an editable combo box. */
    @Test
    fun `when editable on opened popup clicking the textfield doesn't close the popup`() {
        val comboBox = editableListComboBox()

        chevronContainer.performClick()
        popupMenu.assertIsDisplayed()

        comboBox.performClick()
        popupMenu.assertIsDisplayed()
    }

    /**
     * Verifies that clicking the chevron opens the popup and allows selection of the first item in an editable combo
     * box.
     */
    @Test
    fun `when editable clicking chevron open the popup and select the first item`() {
        // Use the direct composition so we can track the selection state
        val focusRequester = FocusRequester()
        var selectedIndex = 0

        composeRule.setContent {
            IntUiTheme {
                EditableListComboBox(
                    items = comboBoxItems,
                    initialSelectedIndex = selectedIndex,
                    onSelectedItemChange = { index: Int, text: String -> selectedIndex = index },
                    modifier = Modifier.testTag("ComboBox").width(200.dp).focusRequester(focusRequester),
                    isEnabled = true,
                    itemKeys = { index: Int, item: String -> index }, // Explicitly use index as key for tests
                    listState = rememberSelectableLazyListState(),
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

        // Verify first item is visible in the popup
        composeRule.onAllNodesWithText("Item 1", useUnmergedTree = true).onLast().assertIsDisplayed()

        // Verify selection state through selectedIndex and text field
        assert(selectedIndex == 0) { "Expected selectedIndex to be 0, but was $selectedIndex" }
        textField.assertTextEquals("Item 1")
    }

    /** Tests that pressing down twice selects the second element in an editable combo box. */
    @Test
    fun `when editable pressing down twice selects the second element`() {
        // Use the direct composition so we can track the selection state
        val focusRequester = FocusRequester()
        var selectedIndex = 0

        composeRule.setContent {
            IntUiTheme {
                EditableListComboBox(
                    items = comboBoxItems,
                    initialSelectedIndex = selectedIndex,
                    onSelectedItemChange = { index: Int, text: String -> selectedIndex = index },
                    modifier = Modifier.testTag("ComboBox").width(200.dp).focusRequester(focusRequester),
                    isEnabled = true,
                    itemKeys = { index: Int, item: String -> index }, // Explicitly use index as key for tests
                    listState = rememberSelectableLazyListState(),
                )
            }
        }

        // Wait for initial composition
        composeRule.waitForIdle()

        // Request focus and verify initial state
        focusRequester.requestFocus()
        composeRule.waitForIdle()

        textField.assertIsDisplayed().assertIsFocused()
        popupMenu.assertDoesNotExist()

        // Press down to open popup
        textField.performKeyInput {
            keyDown(Key.DirectionDown)
            keyUp(Key.DirectionDown)
        }
        composeRule.waitForIdle()

        // Verify popup is open and first item is selected
        popupMenu.assertIsDisplayed()
        composeRule.onAllNodesWithText("Item 1", useUnmergedTree = true).onLast().assertIsDisplayed()
        assert(selectedIndex == 0) { "Expected selectedIndex to be 0 after opening popup, but was $selectedIndex" }

        // Press down again to select second item
        textField.performKeyInput {
            keyDown(Key.DirectionDown)
            keyUp(Key.DirectionDown)
        }
        composeRule.waitForIdle()

        // Verify second item is selected
        composeRule.onAllNodesWithText("Item 2", useUnmergedTree = true).onLast().assertIsDisplayed()
        assert(selectedIndex == 1) { "Expected selectedIndex to be 1 after second down press, but was $selectedIndex" }

        // Verify the text field shows the right item
        textField.assertTextEquals("Item 2")
    }

    /** Tests that pressing enter selects the previewed item in a non-editable combo box. */
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

    /** Tests that a stateless ListComboBox properly displays and allows selection of the initial selected item. */
    @Test
    fun `stateless ListComboBox displays and selects initial selectedIndex item`() {
        var selectedIndex = 2
        var selectedText = "Item 3"

        composeRule.setContent {
            IntUiTheme {
                ListComboBox(
                    items = comboBoxItems,
                    initialSelectedIndex = selectedIndex,
                    onItemSelected = { index ->
                        selectedIndex = index
                        selectedText = comboBoxItems[index]
                    },
                    modifier = Modifier.testTag("ComboBox").width(200.dp),
                    itemKeys = { index: Int, item: String -> index },
                    listState = rememberSelectableLazyListState(),
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

    /** Verifies that the ListComboBox updates properly when the selected index changes externally. */
    @Test
    fun `when selectedIndex changes externally ListComboBox updates`() {
        var selectedIndex by mutableStateOf(0)

        composeRule.setContent {
            IntUiTheme {
                ListComboBox(
                    items = comboBoxItems,
                    initialSelectedIndex = selectedIndex,
                    onItemSelected = { index -> selectedIndex = index },
                    modifier = Modifier.testTag("ComboBox").width(200.dp),
                    itemKeys = { index: Int, item: String -> index },
                    listState = rememberSelectableLazyListState(),
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

    /** Tests that editing text in an editable ListComboBox does not affect the selected index. */
    @Test
    fun `when editable ListComboBox text is edited then selectedIndex remains unchanged`() {
        var selectedIndex = 2
        val focusRequester = FocusRequester()

        composeRule.setContent {
            IntUiTheme {
                EditableListComboBox(
                    items = comboBoxItems,
                    initialSelectedIndex = selectedIndex,
                    onSelectedItemChange = { index: Int, _: String -> selectedIndex = index },
                    modifier = Modifier.testTag("ComboBox").width(200.dp).focusRequester(focusRequester),
                    itemKeys = { index: Int, item: String -> index },
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

    /** Verifies that the text field updates when the selected index changes in an editable ListComboBox. */
    @Test
    fun `when editable ListComboBox selectedIndex changes then text field updates`() {
        var selectedIndex by mutableStateOf(0)
        val focusRequester = FocusRequester()

        composeRule.setContent {
            IntUiTheme {
                EditableListComboBox(
                    items = comboBoxItems,
                    initialSelectedIndex = selectedIndex,
                    onSelectedItemChange = { index: Int, _: String -> selectedIndex = index },
                    modifier = Modifier.testTag("ComboBox").width(200.dp).focusRequester(focusRequester),
                    itemKeys = { index: Int, item: String -> index },
                    listState = rememberSelectableLazyListState(),
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

    /**
     * Helper function to create and set up an editable combo box for testing. Initializes the combo box with default
     * settings and ensures it has focus.
     */
    private fun editableListComboBox(): SemanticsNodeInteraction {
        val focusRequester = FocusRequester()
        composeRule.setContent {
            IntUiTheme {
                EditableListComboBox(
                    items = comboBoxItems,
                    initialSelectedIndex = 0,
                    onSelectedItemChange = { _: Int, _: String -> },
                    modifier = Modifier.testTag("ComboBox").width(200.dp).focusRequester(focusRequester),
                    isEnabled = true,
                    itemKeys = { index: Int, item: String -> index }, // Explicitly use index as key for tests
                    listState = rememberSelectableLazyListState(),
                )
            }
        }
        focusRequester.requestFocus()
        val comboBox = comboBox
        comboBox.assertIsDisplayed()

        textField.assertIsDisplayed().assertIsFocused()
        return comboBox
    }

    /**
     * Helper function to create and set up a disabled editable combo box for testing. Used to verify behavior when the
     * combo box is in a disabled state.
     */
    private fun disabledEditableListComboBox(): SemanticsNodeInteraction {
        val focusRequester = FocusRequester()
        injectEditableListComboBox(focusRequester, isEnabled = false)
        focusRequester.requestFocus()
        val comboBox = comboBox
        comboBox.assertIsDisplayed()
        textField.assertIsDisplayed()
        return comboBox
    }

    /**
     * Helper function to create and set up a focused non-editable combo box for testing.
     *
     * @param focusRequester The FocusRequester to manage focus
     * @param isEnabled Whether the combo box should be enabled
     */
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

    /**
     * Helper function to inject a non-editable combo box into the test environment.
     *
     * @param focusRequester The FocusRequester to manage focus
     * @param isEnabled Whether the combo box should be enabled
     */
    private fun injectListComboBox(focusRequester: FocusRequester, isEnabled: Boolean) {
        composeRule.setContent {
            IntUiTheme {
                ListComboBox(
                    items = comboBoxItems,
                    initialSelectedIndex = 0,
                    onItemSelected = {},
                    modifier = Modifier.testTag("ComboBox").width(200.dp).focusRequester(focusRequester),
                    isEnabled = isEnabled,
                    itemKeys = { index: Int, item: String -> index },
                )
            }
        }
    }

    /**
     * Helper function to inject an editable combo box into the test environment.
     *
     * @param focusRequester The FocusRequester to manage focus
     * @param isEnabled Whether the combo box should be enabled
     */
    private fun injectEditableListComboBox(focusRequester: FocusRequester, isEnabled: Boolean) {
        composeRule.setContent {
            IntUiTheme {
                EditableListComboBox(
                    items = comboBoxItems,
                    initialSelectedIndex = 0,
                    onSelectedItemChange = { _: Int, _: String -> },
                    modifier = Modifier.testTag("ComboBox").width(200.dp).focusRequester(focusRequester),
                    isEnabled = isEnabled,
                    itemKeys = { index: Int, item: String -> index },
                    listState = rememberSelectableLazyListState(),
                )
            }
        }
    }

    /** Sample items used across all tests. Provides a mix of short and long items to test various display scenarios. */
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
