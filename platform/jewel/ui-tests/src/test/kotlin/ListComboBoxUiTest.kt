import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.isDisplayed
import androidx.compose.ui.test.isNotDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.unit.dp
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.intui.standalone.theme.IntUiTheme
import org.jetbrains.jewel.ui.component.EditableComboBox
import org.jetbrains.jewel.ui.component.ListComboBox
import org.jetbrains.jewel.ui.component.ListItemState
import org.jetbrains.jewel.ui.component.SimpleListItem
import org.jetbrains.jewel.ui.theme.simpleListItemStyle
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalTestApi::class)
class ListComboBoxUiTest {
    @get:Rule val composeRule = createComposeRule()

    private val popupMenu: SemanticsNodeInteraction
        get() = composeRule.onNode(hasTestTag("Jewel.ComboBox.PopupMenu"))

    private val chevronContainer: SemanticsNodeInteraction
        get() = composeRule.onNodeWithTag("Jewel.ComboBox.ChevronContainer", useUnmergedTree = true)

    private val textField: SemanticsNodeInteraction
        get() = composeRule.onNodeWithTag("Jewel.ComboBox.TextField")

    private val comboBox: SemanticsNodeInteraction
        get() = composeRule.onNodeWithTag("ComboBox")

    @Test
    fun `when enabled and editable clicking the chevron container opens the popup`() {
        editableComboBox()
        chevronContainer.assertExists().assertHasClickAction().performClick()
        popupMenu.assertExists()
    }

    @Test
    fun `when disable clicking the chevron container doesn't open the popup`() {
        injectComboBox(FocusRequester(), isEditable = false, isEnabled = false)
        chevronContainer.assertExists().assertHasNoClickAction().performClick()
        popupMenu.assertDoesNotExist()
    }

    @Test
    fun `when editable clicking chevron twice opens and closed the popup`() {
        injectComboBox(FocusRequester(), isEditable = true, isEnabled = true)

        chevronContainer.assertHasClickAction().performClick()
        popupMenu.assertExists().isDisplayed()

        chevronContainer.performClick()
        popupMenu.assertDoesNotExist()
    }

    @Test
    fun `TAB navigation focuses only the text field`() {
        val focusRequester = FocusRequester()
        composeRule.setContent {
            IntUiTheme {
                Box(modifier = Modifier.size(20.dp).focusRequester(focusRequester).testTag("Pre-Box").focusable(true))
                EditableComboBox(
                    modifier = Modifier.width(140.dp).testTag("ComboBox"),
                    inputTextFieldState = rememberTextFieldState("Item 1"),
                    popupContent = { /* ... */ },
                )
            }
        }
        focusRequester.requestFocus()
        composeRule.onNodeWithTag("Pre-Box").assertIsDisplayed().assertIsFocused()

        composeRule.onNodeWithTag("Pre-Box").performKeyInput {
            keyDown(Key.Tab)
            keyUp(Key.Tab)
        }

        composeRule.onNodeWithText("Item 1").assertIsDisplayed().assertIsFocused()

        composeRule.onNodeWithText("Item 1").performKeyInput {
            keyDown(Key.Tab)
            keyUp(Key.Tab)
        }

        composeRule.onNodeWithTag("Pre-Box").assertIsFocused()
    }

    @Test
    fun `when not-editable both Box and TextField are focused`() {
        notEditableFocusedComboBox()
        composeRule.onNodeWithText("Item 1").assertIsDisplayed().assertIsFocused()
    }

    @Test
    fun `when not-editable click opens popup`() {
        val comboBox = notEditableFocusedComboBox()
        comboBox.performClick()
        composeRule.onNodeWithText("Item 2").isDisplayed()
    }

    @Test
    fun `when not-editable click on comboBox opens popup`() {
        val comboBox = notEditableFocusedComboBox()

        comboBox.performClick()
        popupMenu.isDisplayed()
    }

    @Test
    fun `when not-editable double click on comboBox opens and closes popup`() {
        val comboBox = notEditableFocusedComboBox()

        comboBox.performClick()
        popupMenu.isDisplayed()

        composeRule.waitForIdle()

        comboBox.performClick()
        popupMenu.assertDoesNotExist()
    }

    @Test
    fun `when not-editable pressing spacebar opens popup`() {
        val comboBox = notEditableFocusedComboBox()

        popupMenu.assertDoesNotExist()

        comboBox.performKeyInput {
            keyDown(Key.Spacebar)
            keyUp(Key.Spacebar)
        }
        popupMenu.isDisplayed()
    }

    // Reference: https://youtrack.jetbrains.com/issue/CMP-3710
    //    @Test
    //    fun `when editable pressing spacebar does not open popup`() {
    //        val comboBox = editableComboBox()
    //        popupMenu.assertDoesNotExist()
    //
    //        textField.assertIsFocused().isDisplayed()
    //        textField.assertTextContains("Item 1")
    //        textField.assertIsFocused().performKeyInput {
    //            keyDown(Key.Spacebar)
    //            keyUp(Key.Spacebar)
    //        }
    //        textField.assertTextEquals("Item 1 ")
    //        popupMenu.assertDoesNotExist()
    //    }

    @Test
    fun `when not-editable pressing enter does not open popup`() {
        val comboBox = notEditableFocusedComboBox()

        popupMenu.assertDoesNotExist()

        comboBox.performKeyInput {
            keyDown(Key.Enter)
            keyUp(Key.Enter)
        }
        composeRule.onNodeWithText("Item 1").isNotDisplayed()
        popupMenu.assertDoesNotExist()
    }

    @Test
    fun `when editable pressing enter does not open popup`() {
        val comboBox = editableComboBox()

        popupMenu.assertDoesNotExist()

        comboBox.performKeyInput {
            keyDown(Key.Enter)
            keyUp(Key.Enter)
        }
        composeRule.onNodeWithText("Item 1").assertIsDisplayed()
        popupMenu.assertDoesNotExist()
    }

    @Test
    fun `when editable, textField is displayed and can receive input`() {
        injectComboBox(FocusRequester(), isEditable = true, isEnabled = true)

        composeRule.onNodeWithTag("ComboBox").assertIsDisplayed().performClick()

        composeRule.onNodeWithText("Item 1").assertIsDisplayed().assertIsFocused().performTextClearance()
        composeRule.onNodeWithText("").performTextInput("Item 1 edited")

        composeRule.onNodeWithText("Item 1 edited").assertIsDisplayed()
    }

    @Suppress("SwallowedException")
    @Test
    fun `when not editable, only Text component is displayed and cannot be edited`() {
        injectComboBox(FocusRequester(), isEditable = false, isEnabled = true)

        composeRule.onNodeWithTag("ComboBox").assertIsDisplayed()

        val textNode = composeRule.onNodeWithText("Item 1")
        textNode.assertIsDisplayed()

        var exceptionThrown = false
        try {
            textNode.performTextClearance()
        } catch (e: AssertionError) {
            exceptionThrown = true
        }
        assert(exceptionThrown) { "Expected an AssertionError to be thrown when attempting to clear text" }
        textNode.assertIsDisplayed()
    }

    @Test
    fun `when disabled, ComboBox cannot be interacted with`() {
        val comboBox = disabledEditableComboBox()
        comboBox.assertIsDisplayed().assertHasNoClickAction().performClick()
        popupMenu.assertDoesNotExist()

        // BasicTextField clickable adds an onClick action even when BTF is disabled
        // textField.assertIsDisplayed().assertIsNotEnabled().assertHasNoClickAction().performClick() ❌
        textField.assertIsDisplayed().assertIsNotEnabled().performClick()
        popupMenu.assertDoesNotExist()
    }

    @Test
    fun `when editable divider is displayed`() {
        injectComboBox(FocusRequester(), isEditable = true, isEnabled = true)

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
        injectComboBox(FocusRequester(), isEditable = false, isEnabled = true)
        composeRule
            .onNode(
                hasTestTag("Jewel.ComboBox.Divider") and hasContentDescription("Jewel.ComboBox.Divider"),
                useUnmergedTree = true,
            )
            .assertDoesNotExist()
    }

    @Test
    fun `when not-editable ChevronContainer is clickable and opens popup`() {
        injectComboBox(FocusRequester(), isEditable = false, isEnabled = true)

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
        editableComboBox()
        popupMenu.assertDoesNotExist()
        textField.performKeyInput {
            keyDown(Key.DirectionDown)
            keyUp(Key.DirectionDown)
        }
        popupMenu.assertIsDisplayed()
    }

    @Test
    fun `when focused and editable pressing arrow down twice opens the popup and selects the second item`() {
        editableComboBox()
        popupMenu.assertDoesNotExist()
        textField.performKeyInput {
            keyDown(Key.DirectionDown)
            keyUp(Key.DirectionDown)
            keyDown(Key.DirectionDown)
            keyUp(Key.DirectionDown)
        }
        popupMenu.assertIsDisplayed()
        composeRule.onAllNodesWithText("Item 2").onLast().isDisplayed()
    }

    @Test
    fun `when enabled but not editable clicking on the comboBox focuses it and open the popup`() {
        val focusRequester = FocusRequester()
        injectComboBox(focusRequester, false, true)
        val comboBox = comboBox

        comboBox.performClick()
        comboBox.assertIsFocused()
        popupMenu.assertIsDisplayed()
    }

    @Test
    fun `when enabled but not editable spacebar opens the popup`() {
        val comboBox = notEditableFocusedComboBox()
        comboBox.performKeyInput {
            keyDown(Key.Spacebar)
            keyUp(Key.Spacebar)
        }
        popupMenu.assertIsDisplayed()
    }

    @Test
    fun `when enabled but not editable pressing spacebar twice opens and closes the popup`() {
        val comboBox = notEditableFocusedComboBox()
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
        injectComboBox(focusRequester, isEditable = true, isEnabled = true)
        focusRequester.requestFocus()

        val comboBox = comboBox
        comboBox.assertIsDisplayed()
        chevronContainer.performClick()
        popupMenu.assertIsDisplayed()

        composeRule.waitForIdle()

        focusRequester.freeFocus()
        popupMenu.isDisplayed()
    }

    @Test
    fun `when editable pressing enter on opened popup closes it`() {
        val comboBox = editableComboBox()

        chevronContainer.performClick()
        popupMenu.assertIsDisplayed()

        comboBox.performClick()
        popupMenu.isDisplayed()
        comboBox.performKeyInput {
            keyDown(Key.Enter)
            keyUp(Key.Enter)
        }
        popupMenu.assertDoesNotExist()
    }

    @Test
    fun `when editable on opened popup clicking the textfield doesn't close the popup`() {
        val comboBox = editableComboBox()

        chevronContainer.performClick()
        popupMenu.assertIsDisplayed()

        comboBox.performClick()
        popupMenu.isDisplayed()
    }

    @Test
    fun `when editable clicking chevron open the popup and select the first item`() {
        editableComboBox()
        chevronContainer.performClick()
        composeRule.onNodeWithTag("Item 1").assertIsDisplayed().assertIsSelected()
    }

    @Test
    fun `when editable pressing down twice selects the second element`() {
        editableComboBox()
        textField.performKeyInput {
            keyDown(Key.DirectionDown)
            keyUp(Key.DirectionDown)
        }
        popupMenu.assertIsDisplayed()

        textField.performKeyInput {
            keyDown(Key.DirectionDown)
            keyUp(Key.DirectionDown)
        }
        composeRule.onNodeWithTag("Item 2").assertIsDisplayed().assertIsSelected()
    }

    private fun editableComboBox(): SemanticsNodeInteraction {
        val focusRequester = FocusRequester()
        injectComboBox(focusRequester, isEditable = true, isEnabled = true)
        focusRequester.requestFocus()
        val comboBox = comboBox
        comboBox.assertIsDisplayed()

        textField.assertIsDisplayed().assertIsFocused()
        return comboBox
    }

    private fun disabledEditableComboBox(): SemanticsNodeInteraction {
        val focusRequester = FocusRequester()
        injectComboBox(focusRequester, true, false)
        focusRequester.requestFocus()
        val comboBox = comboBox
        comboBox.assertIsDisplayed()
        textField.assertIsDisplayed()
        return comboBox
    }

    private fun notEditableFocusedComboBox(
        focusRequester: FocusRequester = FocusRequester(),
        isEnabled: Boolean = true,
    ): SemanticsNodeInteraction {
        injectComboBox(focusRequester, false, isEnabled)
        focusRequester.requestFocus()
        val comboBox = comboBox
        comboBox.assertIsDisplayed().assertIsFocused()
        return comboBox
    }

    private fun injectComboBox(focusRequester: FocusRequester, isEditable: Boolean, isEnabled: Boolean) {
        composeRule.setContent {
            IntUiTheme {
                var selectedComboBox: String? by remember { mutableStateOf(itemsComboBox.first()) }
                ListComboBox(
                    items = itemsComboBox,
                    modifier = Modifier.testTag("ComboBox").width(200.dp).focusRequester(focusRequester),
                    isEditable = isEditable,
                    isEnabled = isEnabled,
                    onSelectedItemChange = { selectedComboBox = it },
                    listItemContent = { item, isSelected, _, isItemHovered, previewSelection ->
                        SimpleListItem(
                            text = item,
                            modifier = Modifier.testTag(item),
                            state = ListItemState(isSelected, isItemHovered, previewSelection),
                            style = JewelTheme.simpleListItemStyle,
                            contentDescription = item,
                        )
                    },
                )
            }
        }
    }
}

private val itemsComboBox =
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
