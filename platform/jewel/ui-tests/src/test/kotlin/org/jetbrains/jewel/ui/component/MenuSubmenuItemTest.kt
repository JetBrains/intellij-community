package org.jetbrains.jewel.ui.component

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isPopup
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.unit.dp
import org.jetbrains.jewel.foundation.JewelFlags
import org.jetbrains.jewel.intui.standalone.theme.IntUiTheme
import org.jetbrains.jewel.ui.component.interactions.performKeyPress
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class MenuSubmenuItemTest {
    @get:Rule val rule = createComposeRule()

    @Test
    fun `clicking submenu item opens submenu`() {
        val selected = mutableStateOf(false)

        rule.setContent {
            IntUiTheme {
                CompositionLocalProvider(LocalMenuController provides FakeMenuController()) {
                    MenuSubmenuItem(
                        showIcon = false,
                        selected = selected.value,
                        onSelectedChange = { selected.value = it },
                        submenu = { selectableItem(false, onClick = {}) { Text("Submenu") } },
                        content = { Text("Parent") },
                    )
                }
            }
        }

        // submenu not yet visible
        rule.onNode(hasText("Submenu").and(hasAnyAncestor(isPopup()))).assertDoesNotExist()

        rule.onNodeWithText("Parent").performClick()
        rule.waitForIdle()

        rule.onNode(hasText("Submenu").and(hasAnyAncestor(isPopup()))).assertIsDisplayed()
    }

    @Test
    fun `disabling while submenu is open closes the submenu`() {
        val enabled = mutableStateOf(true)
        val selected = mutableStateOf(false)

        rule.setContent {
            IntUiTheme {
                CompositionLocalProvider(LocalMenuController provides FakeMenuController()) {
                    MenuSubmenuItem(
                        showIcon = false,
                        selected = selected.value,
                        onSelectedChange = { selected.value = it },
                        enabled = enabled.value,
                        submenu = { selectableItem(false, onClick = {}) { Text("Submenu") } },
                        content = { Text("Parent") },
                    )
                }
            }
        }

        rule.onNodeWithText("Parent").performClick()
        rule.waitForIdle()
        rule.onNode(hasText("Submenu").and(hasAnyAncestor(isPopup()))).assertIsDisplayed()

        enabled.value = false
        rule.waitForIdle()

        rule.onNode(hasText("Submenu").and(hasAnyAncestor(isPopup()))).assertDoesNotExist()
    }

    @Test
    fun `disabled items cannot be opened`() {
        rule.setContent {
            IntUiTheme {
                CompositionLocalProvider(LocalMenuController provides FakeMenuController()) {
                    MenuSubmenuItem(
                        showIcon = false,
                        selected = false,
                        onSelectedChange = {},
                        enabled = false,
                        submenu = { selectableItem(selected = false, onClick = {}) { Text("Submenu") } },
                        content = { Text("Parent") },
                    )
                }
            }
        }

        rule.onNodeWithText("Parent").performClick()
        rule.waitForIdle()

        rule.onNode(hasText("Submenu").and(hasAnyAncestor(isPopup()))).assertDoesNotExist()
    }

    @Test
    fun `right arrow opens submenu`() {
        val selected = mutableStateOf(false)

        rule.setContent {
            IntUiTheme {
                CompositionLocalProvider(LocalMenuController provides FakeMenuController()) {
                    MenuSubmenuItem(
                        showIcon = false,
                        selected = selected.value,
                        onSelectedChange = { selected.value = it },
                        submenu = { selectableItem(selected = false, onClick = {}) { Text("Submenu") } },
                        content = { Text("Parent") },
                    )
                }
            }
        }

        rule.onNodeWithText("Parent").performSemanticsAction(SemanticsActions.RequestFocus)
        rule.waitForIdle()

        rule.onNodeWithText("Parent").performKeyPress(Key.DirectionRight)
        rule.waitForIdle()

        rule.onNode(hasText("Submenu").and(hasAnyAncestor(isPopup()))).assertIsDisplayed()
    }

    @Test
    fun `clicking hovered submenu item in PopupMenu keeps submenu open`() {
        rule.setContent {
            IntUiTheme {
                PopupMenu(onDismissRequest = { true }, horizontalAlignment = Alignment.Start) {
                    submenu(submenu = { selectableItem(selected = false, onClick = {}) { Text("Submenu") } }) {
                        Text("Parent")
                    }
                }
            }
        }

        rule.onNodeWithText("Parent").performMouseInput { moveTo(center) }
        rule.waitForIdle()
        rule.onNode(hasText("Submenu").and(hasAnyAncestor(isPopup()))).assertIsDisplayed()

        rule.onNodeWithText("Parent").performClick()
        rule.waitForIdle()
        rule.onNode(hasText("Submenu").and(hasAnyAncestor(isPopup()))).assertIsDisplayed()
    }

    @Test
    fun `nested submenus align vertically and sit flush horizontally with parent menu`() {
        val previousFlag = JewelFlags.useCustomPopupRenderer
        JewelFlags.useCustomPopupRenderer = false
        try {
            rule.setContent {
                IntUiTheme {
                    Box(modifier = Modifier.size(600.dp, 300.dp)) {
                        PopupMenu(
                            onDismissRequest = { true },
                            horizontalAlignment = Alignment.Start,
                            modifier = Modifier.testTag("rootMenu"),
                        ) {
                            submenu(
                                submenu = {
                                    submenu(
                                        submenu = { selectableItem(selected = false, onClick = {}) { Text("Leaf 1") } }
                                    ) {
                                        Text("Level 2")
                                    }
                                    selectableItem(selected = false, onClick = {}) { Text("Other 2") }
                                }
                            ) {
                                Text("Level 1")
                            }
                            selectableItem(selected = false, onClick = {}) { Text("Other 1") }
                        }
                    }
                }
            }

            rule.waitForIdle()
            rule.onNodeWithText("Level 1").performClick()
            rule.waitForIdle()
            rule.onNodeWithText("Level 2").performClick()
            rule.waitForIdle()
            rule.onNode(hasText("Leaf 1").and(hasAnyAncestor(isPopup()))).assertIsDisplayed()

            val rootMenu = rule.onNodeWithTag("rootMenu").fetchSemanticsNode().boundsInWindow
            val level1Row = rule.onNodeWithText("Level 1").fetchSemanticsNode().boundsInWindow
            val level2Row = rule.onNodeWithText("Level 2").fetchSemanticsNode().boundsInWindow
            val leaf1Row = rule.onNodeWithText("Leaf 1").fetchSemanticsNode().boundsInWindow

            val horizontalPaddingPx = with(rule.density) { 2.dp.toPx() }
            val verticalPaddingPx = with(rule.density) { 7.dp.toPx() }
            assertEquals(horizontalPaddingPx, level1Row.left - rootMenu.left, 0.5f)
            assertEquals(horizontalPaddingPx, rootMenu.right - level1Row.right, 0.5f)
            assertEquals(verticalPaddingPx, level1Row.top - rootMenu.top, 0.5f)

            val submenu1Left = level2Row.left - horizontalPaddingPx
            assertEquals(level1Row.top, level2Row.top, 0.5f)
            assertEquals(rootMenu.right, submenu1Left, 0.5f)

            val submenu1Right = level2Row.right + horizontalPaddingPx
            val submenu2Left = leaf1Row.left - horizontalPaddingPx
            assertEquals(level2Row.top, leaf1Row.top, 0.5f)
            assertEquals(submenu1Right, submenu2Left, 0.5f)
        } finally {
            JewelFlags.useCustomPopupRenderer = previousFlag
        }
    }
}
