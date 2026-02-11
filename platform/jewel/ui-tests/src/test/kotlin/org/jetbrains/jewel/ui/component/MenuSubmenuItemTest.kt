package org.jetbrains.jewel.ui.component

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isPopup
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import org.jetbrains.jewel.intui.standalone.theme.IntUiTheme
import org.jetbrains.jewel.ui.component.interactions.performKeyPress
import org.junit.Rule
import org.junit.Test

class MenuSubmenuItemTest {
    @get:Rule val rule = createComposeRule()

    @Test
    fun `clicking submenu item opens submenu`() {
        rule.setContent {
            IntUiTheme {
                CompositionLocalProvider(LocalMenuController provides FakeMenuController()) {
                    MenuSubmenuItem(
                        showIcon = false,
                        selected = false,
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

        rule.setContent {
            IntUiTheme {
                CompositionLocalProvider(LocalMenuController provides FakeMenuController()) {
                    MenuSubmenuItem(
                        showIcon = false,
                        selected = false,
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
        rule.setContent {
            IntUiTheme {
                CompositionLocalProvider(LocalMenuController provides FakeMenuController()) {
                    MenuSubmenuItem(
                        showIcon = false,
                        selected = false,
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
}
