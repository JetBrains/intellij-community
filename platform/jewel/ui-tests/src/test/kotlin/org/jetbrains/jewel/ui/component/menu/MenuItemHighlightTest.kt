// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jewel.ui.component.menu

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.junit4.createComposeRule
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
import org.jetbrains.jewel.ui.component.IsHoveredKey
import org.jetbrains.jewel.ui.component.PopupMenu
import org.jetbrains.jewel.ui.component.Text
import org.jetbrains.jewel.ui.component.separator
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalTestApi::class)
class MenuItemHighlightTest {
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

    fun SemanticsNodeInteraction.assertIsHovered() = assert(SemanticsMatcher.expectValue(IsHoveredKey, true))

    fun SemanticsNodeInteraction.assertIsNotHovered() = assert(SemanticsMatcher.expectValue(IsHoveredKey, false))
}
