// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jewel.ui.component.menu

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.input.InputMode
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performMouseInput
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.jetbrains.jewel.foundation.JewelFlags
import org.jetbrains.jewel.intui.standalone.theme.IntUiTheme
import org.jetbrains.jewel.ui.component.DefaultMenuController
import org.jetbrains.jewel.ui.component.LocalMenuController
import org.jetbrains.jewel.ui.component.LocalPopupRenderer
import org.jetbrains.jewel.ui.component.MenuSubmenuItem
import org.jetbrains.jewel.ui.component.RecordingPopupRenderer
import org.jetbrains.jewel.ui.component.Text
import org.junit.Rule
import org.junit.Test

class SubmenuDismissRequestTest {
    @get:Rule val rule = createComposeRule()

    @Test
    fun `escape closes the whole menu tree while the parent item is hovered`() {
        val closedWith = openSubmenuWithHoveredParent { dismiss -> dismiss(InputMode.Keyboard) }

        assertEquals(
            listOf(InputMode.Keyboard),
            closedWith,
            "A keyboard dismissal must reach the root menu, even though the pointer rests on the parent item",
        )
    }

    @Test
    fun `a pointer dismissal is still refused while the parent item is hovered`() {
        val closedWith = openSubmenuWithHoveredParent { dismiss -> dismiss(InputMode.Touch) }

        assertTrue(
            closedWith.isEmpty(),
            "A pointer dismissal must stop at the hovered parent item, or clicking it would close and reopen the submenu",
        )
    }

    /**
     * Opens a submenu, leaves the pointer on the parent item, then hands the popup's `onDismissRequest` to [dismiss].
     *
     * @return the input modes that reached the root menu controller.
     */
    private fun openSubmenuWithHoveredParent(dismiss: ((InputMode) -> Unit) -> Unit): List<InputMode> {
        val closedWith = mutableListOf<InputMode>()
        val rootController =
            DefaultMenuController(
                onDismissRequest = { inputMode ->
                    closedWith += inputMode
                    true
                }
            )
        val renderer = RecordingPopupRenderer()

        val previousRenderer = JewelFlags.useCustomPopupRenderer
        JewelFlags.useCustomPopupRenderer = true
        try {
            rule.setContent {
                IntUiTheme {
                    CompositionLocalProvider(
                        LocalMenuController provides rootController,
                        LocalPopupRenderer provides renderer,
                    ) {
                        var submenuOpen by remember { mutableStateOf(false) }
                        MenuSubmenuItem(
                            showIcon = false,
                            selected = submenuOpen,
                            onSelectedChange = { submenuOpen = it },
                            submenu = { selectableItem(selected = false, onClick = {}) { Text("Sub item") } },
                            content = { Text("Open submenu") },
                        )
                    }
                }
            }

            // moveTo, not updatePointerTo: the hovered state keys off Enter, which only a dispatched move produces.
            rule.onNodeWithText("Open submenu").performMouseInput { moveTo(center) }
            rule.waitForIdle()
            rule.onNodeWithText("Open submenu").performClick()
            rule.waitForIdle()

            check(renderer.dismissRequests.isNotEmpty()) { "The submenu did not open, so there is nothing to dismiss" }
            val dismissRequest =
                requireNotNull(renderer.dismissRequests.last()) { "A submenu must always be able to dismiss" }

            rule.runOnIdle { dismiss(dismissRequest) }
            rule.waitForIdle()
        } finally {
            JewelFlags.useCustomPopupRenderer = previousRenderer
        }

        return closedWith
    }
}
