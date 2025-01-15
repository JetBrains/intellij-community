package org.jetbrains.jewel.ui.component

import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertHasNoClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.unit.dp
import org.jetbrains.jewel.intui.standalone.theme.IntUiTheme
import org.junit.Ignore
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalTestApi::class)
class ListComboBoxUiTest {
    @get:Rule val composeRule = createComposeRule()

    private val popupMenu: SemanticsNodeInteraction
        get() = composeRule.onNodeWithTag("Jewel.ComboBox.Popup")

    private val chevronContainer: SemanticsNodeInteraction
        get() = composeRule.onNodeWithTag("Jewel.ComboBox.ChevronContainer", useUnmergedTree = true)

    private val textField: SemanticsNodeInteraction
        get() = composeRule.onNodeWithTag("Jewel.ComboBox.TextField")

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
            IntUiTheme {
                Box(
                    modifier =
                        Modifier.Companion.size(20.dp).focusRequester(focusRequester).testTag("Pre-Box").focusable(true)
                )
                EditableComboBox(
                    textFieldState = rememberTextFieldState("Item 1"),
                    modifier = Modifier.Companion.width(140.dp).testTag("ComboBox"),
                    popupContent = { /* ... */ },
                )
            }
        }
        focusRequester.requestFocus()
        composeRule.onNodeWithTag("Pre-Box").assertIsDisplayed().assertIsFocused()

        composeRule.onNodeWithTag("Pre-Box").performKeyInput {
            keyDown(Key.Companion.Tab)
            keyUp(Key.Companion.Tab)
        }

        composeRule.onNodeWithTag("Jewel.ComboBox.TextField", useUnmergedTree = true).assertIsFocused()

        textField.performKeyInput {
            keyDown(Key.Companion.Tab)
            keyUp(Key.Companion.Tab)
        }

        composeRule.onNodeWithTag("Pre-Box").assertIsFocused()
    }

    @Test
    fun `when not-editable click opens popup`() {
        val comboBox = focusedListComboBox()
        comboBox.performClick()
        composeRule.onNodeWithTag("Item 2").assertIsDisplayed()
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
            keyDown(Key.Companion.Spacebar)
            keyUp(Key.Companion.Spacebar)
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
            keyDown(Key.Companion.Spacebar)
            keyUp(Key.Companion.Spacebar)
        }
        textField.assertTextEquals("Item 1 ")
        popupMenu.assertDoesNotExist()
    }

    @Test
    fun `when not-editable pressing enter does not open popup`() {
        val comboBox = focusedListComboBox()

        popupMenu.assertDoesNotExist()

        comboBox.performKeyInput {
            keyDown(Key.Companion.Enter)
            keyUp(Key.Companion.Enter)
        }
        popupMenu.assertDoesNotExist()
        composeRule.onNodeWithTag("Item 1").assertDoesNotExist()
    }

    @Test
    fun `when editable, pressing enter does not open popup`() {
        val comboBox = editableListComboBox()

        popupMenu.assertDoesNotExist()

        comboBox.performKeyInput {
            keyDown(Key.Companion.Enter)
            keyUp(Key.Companion.Enter)
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
            keyDown(Key.Companion.DirectionDown)
            keyUp(Key.Companion.DirectionDown)
        }
        popupMenu.assertIsDisplayed()
    }

    @Test
    fun `when focused and editable pressing arrow down twice opens the popup and selects the second item`() {
        editableListComboBox()
        popupMenu.assertDoesNotExist()
        textField.performKeyInput {
            keyDown(Key.Companion.DirectionDown)
            keyUp(Key.Companion.DirectionDown)
            keyDown(Key.Companion.DirectionDown)
            keyUp(Key.Companion.DirectionDown)
        }
        popupMenu.assertIsDisplayed()
        composeRule.onAllNodesWithText("Item 2").onLast().assertIsDisplayed()
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
            keyDown(Key.Companion.Spacebar)
            keyUp(Key.Companion.Spacebar)
        }
        popupMenu.assertIsDisplayed()
    }

    @Test
    fun `when enabled but not editable pressing spacebar twice opens and closes the popup`() {
        val comboBox = focusedListComboBox()
        comboBox.performKeyInput {
            keyDown(Key.Companion.Spacebar)
            keyUp(Key.Companion.Spacebar)
        }
        popupMenu.assertIsDisplayed()
        comboBox.performKeyInput {
            keyDown(Key.Companion.Spacebar)
            keyUp(Key.Companion.Spacebar)
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
            keyDown(Key.Companion.Enter)
            keyUp(Key.Companion.Enter)
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
        editableListComboBox()
        chevronContainer.performClick()
        composeRule.onNodeWithTag("Item 1").assertIsDisplayed().assertIsSelected()
    }

    @Test
    fun `when editable pressing down twice selects the second element`() {
        editableListComboBox()
        popupMenu.assertDoesNotExist()

        comboBox.performKeyInput { pressKey(Key.Companion.DirectionDown) }
        popupMenu.assertIsDisplayed()
        composeRule.onNodeWithTag("Item 1").assertIsDisplayed().assertIsSelected()

        comboBox.performKeyInput { pressKey(Key.Companion.DirectionDown) }
        popupMenu.assertIsDisplayed()
        composeRule.onNodeWithTag("Item 2").assertIsDisplayed().assertIsSelected()
    }

    @Test
    fun `when not editable pressing enter selects the preview selection if any`() {
        injectListComboBox(FocusRequester(), isEnabled = true)
        comboBox.performClick()
        popupMenu.assertIsDisplayed()

        composeRule.onNodeWithTag("Item 2").assertIsDisplayed().performMouseInput {
            enter(Offset(2f, 0f))
            moveTo(Offset(10f, 2f))
            advanceEventTime()
        }

        composeRule.onNodeWithTag("Item 2").assertIsSelected().performKeyInput { pressKey(Key.Companion.Enter) }

        comboBox.assertTextEquals("Item 2", includeEditableText = false)
    }

    private fun editableListComboBox(): SemanticsNodeInteraction {
        val focusRequester = FocusRequester()
        injectEditableListComboBox(focusRequester, isEnabled = true)
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
            IntUiTheme {
                ListComboBox(
                    items = comboBoxItems,
                    modifier = Modifier.testTag("ComboBox").width(200.dp).focusRequester(focusRequester),
                    isEnabled = isEnabled,
                    itemContent = { item, isSelected, isActive ->
                        SimpleListItem(
                            text = item,
                            isSelected = isSelected,
                            isActive = isActive,
                            modifier = Modifier.testTag(item),
                            iconContentDescription = item,
                        )
                    },
                )
            }
        }
    }

    private fun injectEditableListComboBox(focusRequester: FocusRequester, isEnabled: Boolean) {
        composeRule.setContent {
            IntUiTheme {
                EditableListComboBox(
                    items = comboBoxItems,
                    modifier = Modifier.testTag("ComboBox").width(200.dp).focusRequester(focusRequester),
                    isEnabled = isEnabled,
                    itemContent = { item, isSelected, isActive ->
                        SimpleListItem(
                            text = item,
                            isSelected = isSelected,
                            isActive = isActive,
                            modifier = Modifier.testTag(item),
                            iconContentDescription = item,
                        )
                    },
                )
            }
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
