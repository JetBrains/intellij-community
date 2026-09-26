// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jewel.ui.component.menu

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsNotFocused
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isPopup
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.unit.dp
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import org.jetbrains.jewel.intui.standalone.theme.IntUiTheme
import org.jetbrains.jewel.ui.component.DefaultButton
import org.jetbrains.jewel.ui.component.IsHoveredKey
import org.jetbrains.jewel.ui.component.PopupMenu
import org.jetbrains.jewel.ui.component.Text
import org.jetbrains.jewel.ui.component.separator
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalTestApi::class)
class MenuItemFocusTest {
    @get:Rule val composeRule = createComposeRule()

    @Test
    fun `menu items can be clicked`() {
        var selectedItem by mutableStateOf<Int?>(null)
        var isOpen by mutableStateOf(true)

        composeRule.setContent {
            IntUiTheme {
                if (isOpen) {
                    Box(modifier = Modifier.size(400.dp, 300.dp)) {
                        PopupMenu(
                            onDismissRequest = {
                                isOpen = false
                                true
                            },
                            horizontalAlignment = Alignment.Start,
                        ) {
                            selectableItem(selected = selectedItem == 0, onClick = { selectedItem = 0 }) {
                                Text("Item 1")
                            }
                            selectableItem(selected = selectedItem == 1, onClick = { selectedItem = 1 }) {
                                Text("Item 2")
                            }
                            selectableItem(selected = selectedItem == 2, onClick = { selectedItem = 2 }) {
                                Text("Item 3")
                            }
                        }
                    }
                }
            }
        }

        composeRule.onNodeWithText("Item 2").performClick()

        assertEquals(1, selectedItem)
    }

    @Test
    fun `keyboard navigation and selection works`() {
        var selectedItem by mutableStateOf<Int?>(null)
        var isOpen by mutableStateOf(true)

        composeRule.setContent {
            IntUiTheme {
                if (isOpen) {
                    Box(modifier = Modifier.size(400.dp, 300.dp).testTag("container")) {
                        PopupMenu(
                            onDismissRequest = {
                                isOpen = false
                                true
                            },
                            horizontalAlignment = Alignment.Start,
                        ) {
                            selectableItem(selected = selectedItem == 0, onClick = { selectedItem = 0 }) {
                                Text("Item 1")
                            }
                            selectableItem(selected = selectedItem == 1, onClick = { selectedItem = 1 }) {
                                Text("Item 2")
                            }
                            selectableItem(selected = selectedItem == 2, onClick = { selectedItem = 2 }) {
                                Text("Item 3")
                            }
                        }
                    }
                }
            }
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("container").performKeyInput {
            pressKey(Key.DirectionDown) // Highlights first item in list
            pressKey(Key.DirectionDown)
            pressKey(Key.Enter)
        }

        composeRule.waitForIdle()

        assertNotNull(selectedItem)
        assertEquals(1, selectedItem)
    }

    @Test
    fun `key press down focuses the first item when the menu was opened by a button`() {
        var selectedItem by mutableStateOf<Int?>(null)
        var isOpen by mutableStateOf(false)

        composeRule.setContent {
            IntUiTheme {
                Box(modifier = Modifier.size(400.dp, 300.dp).testTag("container")) {
                    DefaultButton(onClick = { isOpen = true }) { Text("Open menu") }

                    if (isOpen) {
                        PopupMenu(
                            onDismissRequest = {
                                isOpen = false
                                true
                            },
                            horizontalAlignment = Alignment.Start,
                        ) {
                            selectableItem(selected = selectedItem == 0, onClick = { selectedItem = 0 }) {
                                Text("Item 1")
                            }
                            selectableItem(selected = selectedItem == 1, onClick = { selectedItem = 1 }) {
                                Text("Item 2")
                            }
                        }
                    }
                }
            }
        }

        composeRule.onNodeWithText("Open menu").performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("container").performKeyInput {
            pressKey(Key.DirectionDown) // Highlights first item in list
            pressKey(Key.Enter)
        }

        composeRule.waitForIdle()

        assertEquals(0, selectedItem)
    }

    @Test
    fun `menu with separators key press down should ignore separator`() {
        var selectedItem by mutableStateOf<Int?>(null)
        var isOpen by mutableStateOf(true)

        composeRule.setContent {
            IntUiTheme {
                if (isOpen) {
                    Box(modifier = Modifier.size(400.dp, 300.dp).testTag("container")) {
                        PopupMenu(
                            onDismissRequest = {
                                isOpen = false
                                true
                            },
                            horizontalAlignment = Alignment.Start,
                        ) {
                            selectableItem(selected = selectedItem == 0, onClick = { selectedItem = 0 }) {
                                Text("Item 1")
                            }
                            separator()
                            selectableItem(selected = selectedItem == 1, onClick = { selectedItem = 1 }) {
                                Text("Item 2")
                            }
                        }
                    }
                }
            }
        }

        composeRule.waitForIdle()

        composeRule.onNodeWithTag("container").performKeyInput {
            pressKey(Key.DirectionDown)
            pressKey(Key.DirectionDown) // Should jump over the separator and go to Item 2
            pressKey(Key.Enter)
        }

        assertEquals(1, selectedItem)
    }

    @Test
    fun `hover highlights menu item`() {
        var selectedItem by mutableStateOf<Int?>(null)
        var isOpen by mutableStateOf(true)

        composeRule.setContent {
            IntUiTheme {
                if (isOpen) {
                    Box(modifier = Modifier.size(400.dp, 300.dp)) {
                        PopupMenu(
                            onDismissRequest = {
                                isOpen = false
                                true
                            },
                            horizontalAlignment = Alignment.Start,
                        ) {
                            selectableItem(selected = selectedItem == 0, onClick = { selectedItem = 0 }) {
                                Text("Item 1")
                            }
                            selectableItem(selected = selectedItem == 1, onClick = { selectedItem = 1 }) {
                                Text("Item 2")
                            }
                            selectableItem(selected = selectedItem == 2, onClick = { selectedItem = 2 }) {
                                Text("Item 3")
                            }
                        }
                    }
                }
            }
        }

        composeRule.waitForIdle()

        composeRule
            .onNodeWithText("Item 2")
            .assertIsNotHovered()
            .performMouseInput { enter(center) }
            .assertIsHovered()
            .performMouseInput { exit() }
            .assertIsNotHovered()
            .performClick()

        assertEquals(1, selectedItem)
    }

    @Test
    fun `hover then keyboard navigation works`() {
        var selectedItem by mutableStateOf<Int?>(null)
        var isOpen by mutableStateOf(true)

        composeRule.setContent {
            IntUiTheme {
                if (isOpen) {
                    Box(modifier = Modifier.size(400.dp, 300.dp).testTag("container")) {
                        PopupMenu(
                            onDismissRequest = {
                                isOpen = false
                                true
                            },
                            horizontalAlignment = Alignment.Start,
                        ) {
                            selectableItem(selected = selectedItem == 0, onClick = { selectedItem = 0 }) {
                                Text("Item 1")
                            }
                            selectableItem(selected = selectedItem == 1, onClick = { selectedItem = 1 }) {
                                Text("Item 2")
                            }
                            selectableItem(selected = selectedItem == 2, onClick = { selectedItem = 2 }) {
                                Text("Item 3")
                            }
                        }
                    }
                }
            }
        }

        composeRule.waitForIdle()

        // Hover over Item 2 to set initial focus
        composeRule.onNodeWithText("Item 2").performMouseInput { enter(center) }

        // Use keyboard to navigate down from Item 2 to Item 3
        composeRule.onNodeWithTag("container").performKeyInput {
            pressKey(Key.DirectionDown)
            pressKey(Key.Enter)
        }

        assertEquals(2, selectedItem)
    }

    @Test
    fun `hover over different items without selecting doesnt update selectedItem`() {
        var selectedItem by mutableStateOf<Int?>(null)
        var isOpen by mutableStateOf(true)

        composeRule.setContent {
            IntUiTheme {
                if (isOpen) {
                    Box(modifier = Modifier.size(400.dp, 300.dp)) {
                        PopupMenu(
                            onDismissRequest = {
                                isOpen = false
                                true
                            },
                            horizontalAlignment = Alignment.Start,
                        ) {
                            selectableItem(selected = selectedItem == 0, onClick = { selectedItem = 0 }) {
                                Text("Item 1")
                            }
                            selectableItem(selected = selectedItem == 1, onClick = { selectedItem = 1 }) {
                                Text("Item 2")
                            }
                            selectableItem(selected = selectedItem == 2, onClick = { selectedItem = 2 }) {
                                Text("Item 3")
                            }
                        }
                    }
                }
            }
        }

        composeRule.waitForIdle()

        composeRule
            .onNodeWithText("Item 1")
            .performMouseInput { enter(center) }
            .assertIsHovered()
            .performMouseInput { exit() }

        composeRule
            .onNodeWithText("Item 2")
            .performMouseInput { enter(center) }
            .assertIsHovered()
            .performMouseInput { exit() }
            .assertIsNotHovered()

        assertNull(selectedItem)
    }

    @Test
    fun `pointer leaving a menu item clears its focus`() {
        composeRule.setContent { IntUiTheme { SimpleMenu() } }
        composeRule.waitForIdle()

        composeRule.onNodeWithText("Item 2").performMouseInput { enter(center) }
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Item 2").assertIsFocused()

        composeRule.onNodeWithText("Item 2").performMouseInput { exit() }
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Item 2").assertIsNotFocused()
    }

    @Test
    fun `pointer moving between items moves focus rather than clearing it`() {
        composeRule.setContent { IntUiTheme { SimpleMenu() } }
        composeRule.waitForIdle()

        composeRule.onNodeWithText("Item 1").performMouseInput { enter(center) }
        composeRule.waitForIdle()

        composeRule.onNodeWithText("Item 1").performMouseInput { exit() }
        composeRule.onNodeWithText("Item 2").performMouseInput { enter(center) }
        composeRule.waitForIdle()

        composeRule.onNodeWithText("Item 1").assertIsNotFocused()
        composeRule.onNodeWithText("Item 2").assertIsFocused()
    }

    @Test
    fun `pointer moving backwards between items moves focus rather than clearing it`() {
        composeRule.setContent { IntUiTheme { SimpleMenu() } }
        composeRule.waitForIdle()

        composeRule.onNodeWithText("Item 2").performMouseInput { enter(center) }
        composeRule.waitForIdle()

        // One continuous move, so the exit from Item 2 and the enter into Item 1 land in the same frame the way a real
        // pointer move does. Item 1 sits directly above, so -center.y is its centre.
        composeRule.onNodeWithText("Item 2").performMouseInput { moveTo(Offset(center.x, -center.y)) }
        composeRule.waitForIdle()

        composeRule.onNodeWithText("Item 2").assertIsNotFocused()
        composeRule.onNodeWithText("Item 1").assertIsFocused()
    }

    @Test
    fun `key press down after the pointer leaves the menu focuses the first item`() {
        var selectedItem by mutableStateOf<Int?>(null)

        composeRule.setContent { IntUiTheme { SimpleMenu(onSelect = { selectedItem = it }) } }
        composeRule.waitForIdle()

        // Item 1 rather than the last item: without the disarm, focus would still be here and the press below would
        // move on to Item 2 instead of landing on the first item.
        composeRule.onNodeWithText("Item 1").performMouseInput { enter(center) }
        composeRule.onNodeWithText("Item 1").performMouseInput { exit() }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("container").performKeyInput {
            pressKey(Key.DirectionDown)
            pressKey(Key.Enter)
        }
        composeRule.waitForIdle()

        assertEquals(0, selectedItem)
    }

    @Test
    fun `hovering the parent item clears focus in the open submenu`() {
        composeRule.setContent { IntUiTheme { MenuWithSubmenu() } }
        composeRule.waitForIdle()

        composeRule.onNodeWithText("More options").performMouseInput { enter(center) }
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Submenu item 1").assertIsDisplayed()

        composeRule.onNodeWithText("More options").performMouseInput { exit() }
        composeRule.onNodeWithText("Submenu item 1").performMouseInput { enter(center) }
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Submenu item 1").assertIsFocused()

        composeRule.onNodeWithText("Submenu item 1").performMouseInput { exit() }
        composeRule.onNodeWithText("More options").performMouseInput { enter(center) }
        composeRule.waitForIdle()

        // The submenu stays open, but nothing inside it stays armed (a.k.a, does not have a blue background)
        composeRule.onNodeWithText("Submenu item 1").assertIsDisplayed()
        composeRule.onNodeWithText("Submenu item 1").assertIsNotFocused()
        composeRule.onNodeWithText("Submenu item 2").assertIsNotFocused()
    }

    @Test
    fun `key press down does not open a submenu`() {
        composeRule.setContent { IntUiTheme { MenuWithSubmenu() } }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("container").performKeyInput {
            pressKey(Key.DirectionDown) // Item 1
            pressKey(Key.DirectionDown) // More options
        }
        composeRule.waitForIdle()

        composeRule.onNode(hasText("Submenu item 1").and(hasAnyAncestor(isPopup()))).assertDoesNotExist()
    }

    @Test
    fun `key press right opens the submenu of the focused item`() {
        composeRule.setContent { IntUiTheme { MenuWithSubmenu() } }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("container").performKeyInput {
            pressKey(Key.DirectionDown) // Item 1
            pressKey(Key.DirectionDown) // More options
            pressKey(Key.DirectionRight)
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithText("Submenu item 1").assertIsDisplayed()
    }

    @Test
    fun `key press left closes an open submenu`() {
        composeRule.setContent { IntUiTheme { MenuWithSubmenu() } }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("container").performKeyInput {
            pressKey(Key.DirectionDown) // Item 1
            pressKey(Key.DirectionDown) // More options
            pressKey(Key.DirectionRight)
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Submenu item 1").assertIsDisplayed()

        composeRule.onNodeWithTag("container").performKeyInput { pressKey(Key.DirectionLeft) }
        composeRule.waitForIdle()

        composeRule.onNode(hasText("Submenu item 1").and(hasAnyAncestor(isPopup()))).assertDoesNotExist()
    }

    @Test
    fun `key press down in a submenu opened by keyboard focuses its first item`() {
        composeRule.setContent { IntUiTheme { MenuWithSubmenu() } }
        openSubmenuWithKeyboard()

        composeRule.onNodeWithTag("container").performKeyInput { pressKey(Key.DirectionDown) }
        composeRule.waitForIdle()

        composeRule.onNodeWithText("Submenu item 1").assertIsFocused()
        composeRule.onNodeWithText("Submenu item 2").assertIsNotFocused()
    }

    @Test
    fun `key press up in a submenu opened by keyboard focuses its last item`() {
        composeRule.setContent { IntUiTheme { MenuWithSubmenu() } }
        openSubmenuWithKeyboard()

        composeRule.onNodeWithTag("container").performKeyInput { pressKey(Key.DirectionUp) }
        composeRule.waitForIdle()

        composeRule.onNodeWithText("Submenu item 2").assertIsFocused()
        composeRule.onNodeWithText("Submenu item 1").assertIsNotFocused()
    }

    @Test
    fun `key press down in a submenu opened by hover focuses its first item`() {
        composeRule.setContent { IntUiTheme { MenuWithSubmenu() } }
        openSubmenuByHover()

        composeRule.onNodeWithTag("container").performKeyInput { pressKey(Key.DirectionDown) }
        composeRule.waitForIdle()

        composeRule.onNodeWithText("Submenu item 1").assertIsFocused()
        composeRule.onNodeWithText("Submenu item 2").assertIsNotFocused()
    }

    @Test
    fun `key press up in a submenu opened by hover focuses its last item`() {
        composeRule.setContent { IntUiTheme { MenuWithSubmenu() } }
        openSubmenuByHover()

        composeRule.onNodeWithTag("container").performKeyInput { pressKey(Key.DirectionUp) }
        composeRule.waitForIdle()

        composeRule.onNodeWithText("Submenu item 2").assertIsFocused()
        composeRule.onNodeWithText("Submenu item 1").assertIsNotFocused()
    }

    /** Arms "More options" with the arrow keys and opens its submenu, leaving nothing in the submenu focused. */
    private fun openSubmenuWithKeyboard() {
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("container").performKeyInput {
            pressKey(Key.DirectionDown) // Item 1
            pressKey(Key.DirectionDown) // More options
            pressKey(Key.DirectionRight)
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithText("Submenu item 1").assertIsDisplayed()
        composeRule.onNodeWithText("Submenu item 1").assertIsNotFocused()
        composeRule.onNodeWithText("Submenu item 2").assertIsNotFocused()
    }

    /** Opens the submenu by hovering "More options", leaving the pointer there and nothing in the submenu focused. */
    private fun openSubmenuByHover() {
        composeRule.waitForIdle()

        composeRule.onNodeWithText("More options").performMouseInput { enter(center) }
        composeRule.waitForIdle()

        composeRule.onNodeWithText("Submenu item 1").assertIsDisplayed()
        composeRule.onNodeWithText("Submenu item 1").assertIsNotFocused()
        composeRule.onNodeWithText("Submenu item 2").assertIsNotFocused()
    }

    @Test
    fun `leaving the menu keeps the owner armed when its submenu was opened by keyboard`() {
        composeRule.setContent { IntUiTheme { MenuWithSubmenu() } }
        composeRule.waitForIdle()

        // The pointer stays on Item 1 the whole time
        composeRule.onNodeWithText("Item 1").performMouseInput { enter(center) }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("container").performKeyInput {
            pressKey(Key.DirectionDown) // More options
            pressKey(Key.DirectionRight) // opens the submenu
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Submenu item 1").assertIsDisplayed()

        composeRule.onNodeWithText("Item 1").performMouseInput { exit() }
        composeRule.waitForIdle()

        composeRule.onNodeWithText("More options").assertIsFocused()
    }

    @Test
    fun `leaving the menu disarms the owner after its submenu was closed by keyboard`() {
        composeRule.setContent { IntUiTheme { MenuWithSubmenu() } }
        composeRule.waitForIdle()

        composeRule.onNodeWithText("More options").performMouseInput { enter(center) }
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Submenu item 1").assertIsDisplayed()

        composeRule.onNodeWithTag("container").performKeyInput { pressKey(Key.DirectionLeft) }
        composeRule.waitForIdle()
        composeRule.onNode(hasText("Submenu item 1").and(hasAnyAncestor(isPopup()))).assertDoesNotExist()

        composeRule.onNodeWithText("More options").performMouseInput { exit() }
        composeRule.waitForIdle()

        composeRule.onNodeWithText("More options").assertIsNotFocused()
    }

    @Composable
    private fun SimpleMenu(onSelect: (Int) -> Unit = {}) {
        Box(modifier = Modifier.size(400.dp, 300.dp).testTag("container")) {
            PopupMenu(onDismissRequest = { true }, horizontalAlignment = Alignment.Start) {
                selectableItem(selected = false, onClick = { onSelect(0) }) { Text("Item 1") }
                selectableItem(selected = false, onClick = { onSelect(1) }) { Text("Item 2") }
                selectableItem(selected = false, onClick = { onSelect(2) }) { Text("Item 3") }
            }
        }
    }

    @Composable
    private fun MenuWithSubmenu() {
        Box(modifier = Modifier.size(400.dp, 300.dp).testTag("container")) {
            PopupMenu(onDismissRequest = { true }, horizontalAlignment = Alignment.Start) {
                selectableItem(selected = false, onClick = {}) { Text("Item 1") }
                submenu(
                    submenu = {
                        selectableItem(selected = false, onClick = {}) { Text("Submenu item 1") }
                        selectableItem(selected = false, onClick = {}) { Text("Submenu item 2") }
                    },
                    content = { Text("More options") },
                )
            }
        }
    }

    fun SemanticsNodeInteraction.assertIsHovered() = assert(SemanticsMatcher.expectValue(IsHoveredKey, true))

    fun SemanticsNodeInteraction.assertIsNotHovered() = assert(SemanticsMatcher.expectValue(IsHoveredKey, false))
}
