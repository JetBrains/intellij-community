// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jewel.ui.component

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.input.InputMode
import androidx.compose.ui.test.junit4.createComposeRule
import javax.swing.KeyStroke
import org.jetbrains.jewel.IntUiTestTheme
import org.jetbrains.jewel.intui.standalone.menuShortcut.StandaloneShortcutProvider
import org.jetbrains.jewel.ui.LocalMenuItemShortcutProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

// A fake MenuController that records interactions for assertions.
class FakeMenuController : MenuController {
    val registeredActions = mutableMapOf<KeyStroke, () -> Unit>()
    var clearCount = 0
        private set

    override val onDismissRequest: (InputMode) -> Boolean = { true }

    override fun onHoveredChange(hovered: Boolean) {
        // DO NOTHING
    }

    override fun closeAll(mode: InputMode, force: Boolean) {
        // DO NOTHING
    }

    override fun close(mode: InputMode): Boolean = true

    override fun isRootMenu(): Boolean = true

    override fun isSubmenu(): Boolean = false

    override fun submenuController(onDismissRequest: (InputMode) -> Boolean): MenuController = this

    override fun registerShortcutAction(keyStroke: KeyStroke, action: () -> Unit) {
        registeredActions[keyStroke] = action
    }

    override fun clearShortcutActions() {
        clearCount++
        registeredActions.clear()
    }

    override fun findAndExecuteShortcut(keyStroke: KeyStroke?): Boolean? = null
}

class MenuContentShortcutRegistrationTest {
    @get:Rule val rule = createComposeRule()

    private lateinit var fakeMenuController: FakeMenuController

    private val copyAction = ContextMenuItemOptionAction.CopyMenuItemOptionAction
    private val pasteAction = ContextMenuItemOptionAction.PasteMenuItemOptionAction

    private val copyKeyStroke = StandaloneShortcutProvider.getShortcutKeyStroke(copyAction)!!
    private val pasteKeyStroke = StandaloneShortcutProvider.getShortcutKeyStroke(pasteAction)!!

    private var triggeredActionType: String? = null

    @Before
    fun setUp() {
        fakeMenuController = FakeMenuController()
        triggeredActionType = null
    }

    // Helper to create the content lambda for MenuContent
    private fun createMenuContentLambda(items: List<MenuSelectableItem>): MenuScope.() -> Unit = {
        items.forEach { item ->
            selectableItemWithActionType(
                selected = false,
                onClick = item.onClick,
                enabled = item.isEnabled,
                actionType = item.itemOptionAction,
                content = item.content,
            )
        }
    }

    @Test
    fun `reordering items should not mix up shortcut actions`() {
        val itemsState =
            mutableStateOf(
                listOf(
                    MenuSelectableItem(
                        itemOptionAction = copyAction,
                        onClick = { triggeredActionType = "COPY" },
                        isSelected = true,
                        isEnabled = true,
                        iconKey = null,
                        content = {},
                    ),
                    MenuSelectableItem(
                        itemOptionAction = pasteAction,
                        onClick = { triggeredActionType = "PASTE" },
                        isSelected = true,
                        isEnabled = true,
                        iconKey = null,
                        content = {},
                    ),
                )
            )

        rule.setContent {
            IntUiTestTheme {
                CompositionLocalProvider(
                    LocalMenuController provides fakeMenuController,
                    LocalMenuItemShortcutProvider provides StandaloneShortcutProvider,
                ) {
                    MenuContent(content = createMenuContentLambda(itemsState.value))
                }
            }
        }

        itemsState.value = itemsState.value.reversed()
        rule.waitForIdle()

        assertTrue("Clear should have been called when items reordered", fakeMenuController.clearCount >= 1)
        assertEquals(2, fakeMenuController.registeredActions.size)

        fakeMenuController.registeredActions[copyKeyStroke]?.invoke()
        assertEquals("Copy KeyStroke must still trigger the copy action", "COPY", triggeredActionType)

        fakeMenuController.registeredActions[pasteKeyStroke]?.invoke()
        assertEquals("Paste KeyStroke must still trigger the paste action", "PASTE", triggeredActionType)
    }

    @Test
    fun `initial items should register their shortcuts`() {
        val initialItems =
            listOf(
                MenuSelectableItem(
                    itemOptionAction = copyAction,
                    onClick = { triggeredActionType = "COPY" },
                    isSelected = true,
                    isEnabled = true,
                    iconKey = null,
                    content = {},
                ),
                MenuSelectableItem(
                    itemOptionAction = pasteAction,
                    onClick = { triggeredActionType = "PASTE" },
                    isSelected = true,
                    isEnabled = true,
                    iconKey = null,
                    content = {},
                ),
            )

        rule.setContent {
            IntUiTestTheme {
                CompositionLocalProvider(
                    LocalMenuController provides fakeMenuController,
                    LocalMenuItemShortcutProvider provides StandaloneShortcutProvider,
                ) {
                    MenuContent(content = createMenuContentLambda(initialItems))
                }
            }
        }

        assertEquals("Clear should NOT be called on first composition", 0, fakeMenuController.clearCount)
        assertEquals(2, fakeMenuController.registeredActions.size)
        assertNotNull(fakeMenuController.registeredActions[copyKeyStroke])

        fakeMenuController.registeredActions[copyKeyStroke]?.invoke()
        assertEquals("COPY", triggeredActionType)
    }

    @Test
    fun `disabled items should not have their shortcuts registered`() {
        val items =
            listOf(
                MenuSelectableItem(
                    itemOptionAction = copyAction,
                    onClick = {},
                    isSelected = true,
                    isEnabled = true,
                    iconKey = null,
                    content = {},
                ),
                MenuSelectableItem(
                    itemOptionAction = pasteAction,
                    onClick = {},
                    isEnabled = false, // This one is disabled
                    isSelected = true,
                    iconKey = null,
                    content = {},
                ),
            )

        rule.setContent {
            IntUiTestTheme {
                CompositionLocalProvider(
                    LocalMenuController provides fakeMenuController,
                    LocalMenuItemShortcutProvider provides StandaloneShortcutProvider,
                ) {
                    MenuContent(content = createMenuContentLambda(items))
                }
            }
        }

        assertEquals(
            "Only enabled items should have shortcuts registered",
            1,
            fakeMenuController.registeredActions.size,
        )
        assertNotNull("Enabled item's shortcut should be present", fakeMenuController.registeredActions[copyKeyStroke])
        assertNull("Disabled item's shortcut should be absent", fakeMenuController.registeredActions[pasteKeyStroke])
    }

    @Test
    fun `items without an actionType should not have shortcuts registered`() {
        val items =
            listOf(
                MenuSelectableItem(
                    itemOptionAction = copyAction,
                    onClick = {},
                    isSelected = true,
                    isEnabled = true,
                    iconKey = null,
                    content = {},
                ),
                MenuSelectableItem(
                    itemOptionAction = null, // No action type
                    onClick = {},
                    isSelected = true,
                    isEnabled = true,
                    iconKey = null,
                    content = {},
                ),
            )

        rule.setContent {
            IntUiTestTheme {
                CompositionLocalProvider(
                    LocalMenuController provides fakeMenuController,
                    LocalMenuItemShortcutProvider provides StandaloneShortcutProvider,
                ) {
                    MenuContent(content = createMenuContentLambda(items))
                }
            }
        }

        assertEquals("Only items with actionType should have shortcuts", 1, fakeMenuController.registeredActions.size)
        assertNotNull(fakeMenuController.registeredActions[copyKeyStroke])
        assertEquals(false, fakeMenuController.registeredActions.containsKey(pasteKeyStroke)) // Just to be explicit
    }

    @Test
    fun `empty list of items should result in no registered shortcuts`() {
        val itemsState =
            mutableStateOf(
                listOf(
                    MenuSelectableItem(
                        itemOptionAction = copyAction,
                        onClick = {},
                        isSelected = true,
                        isEnabled = true,
                        iconKey = null,
                        content = {},
                    )
                )
            )
        rule.setContent {
            IntUiTestTheme {
                CompositionLocalProvider(
                    LocalMenuController provides fakeMenuController,
                    LocalMenuItemShortcutProvider provides StandaloneShortcutProvider,
                ) {
                    MenuContent(content = createMenuContentLambda(itemsState.value))
                }
            }
        }
        assertEquals("Should start with one registered shortcut", 1, fakeMenuController.registeredActions.size)

        itemsState.value = emptyList()
        rule.waitForIdle()

        assertTrue("Clear should have been called when list became empty", fakeMenuController.clearCount == 1)
        assertTrue(
            "No shortcuts should be registered for an empty list",
            fakeMenuController.registeredActions.isEmpty(),
        )
    }

    @Test
    fun `toggling an item's enabled state should re-register shortcuts`() {
        val itemsState =
            mutableStateOf(
                listOf(
                    MenuSelectableItem(
                        itemOptionAction = copyAction,
                        onClick = {},
                        isSelected = true,
                        isEnabled = true,
                        iconKey = null,
                        content = {},
                    )
                )
            )
        rule.setContent {
            IntUiTestTheme {
                CompositionLocalProvider(
                    LocalMenuController provides fakeMenuController,
                    LocalMenuItemShortcutProvider provides StandaloneShortcutProvider,
                ) {
                    MenuContent(content = createMenuContentLambda(itemsState.value))
                }
            }
        }
        assertEquals("Enabled item should be registered initially", 1, fakeMenuController.registeredActions.size)

        itemsState.value =
            listOf(
                MenuSelectableItem(
                    itemOptionAction = copyAction,
                    onClick = {},
                    isSelected = true,
                    isEnabled = false, // Toggled state
                    iconKey = null,
                    content = {},
                )
            )
        rule.waitForIdle()

        assertTrue("Clear should be called again when item is disabled", fakeMenuController.clearCount == 1)
        assertTrue("Disabled item should not be registered", fakeMenuController.registeredActions.isEmpty())

        itemsState.value =
            listOf(
                MenuSelectableItem(
                    itemOptionAction = copyAction,
                    onClick = {},
                    isSelected = true,
                    isEnabled = true, // Re-enabled state
                    iconKey = null,
                    content = {},
                )
            )
        rule.waitForIdle()

        assertTrue("Clear should be called when item is re-enabled", fakeMenuController.clearCount == 2)
        assertEquals("Re-enabled item should be registered again", 1, fakeMenuController.registeredActions.size)
        assertNotNull(fakeMenuController.registeredActions[copyKeyStroke])
    }

    @Test
    fun `changing the menu manager should re-register shortcuts`() {
        val items =
            listOf(
                MenuSelectableItem(
                    itemOptionAction = copyAction,
                    onClick = {},
                    isSelected = true,
                    isEnabled = true,
                    iconKey = null,
                    content = {},
                )
            )
        val currentMenuController = mutableStateOf<MenuController>(fakeMenuController)

        rule.setContent {
            IntUiTestTheme {
                CompositionLocalProvider(
                    LocalMenuController provides currentMenuController.value,
                    LocalMenuItemShortcutProvider provides StandaloneShortcutProvider,
                ) {
                    MenuContent(content = createMenuContentLambda(items))
                }
            }
        }
        assertEquals("Initial manager should have shortcuts", 1, fakeMenuController.registeredActions.size)

        val newFakeMenuController = FakeMenuController()
        currentMenuController.value = newFakeMenuController
        rule.waitForIdle()

        assertEquals("Old manager should be cleared on dispose", 1, fakeMenuController.clearCount)
        assertTrue("Old manager should have no actions", fakeMenuController.registeredActions.isEmpty())

        assertEquals("New manager should not clear on its first composition", 0, newFakeMenuController.clearCount)
        assertEquals("New manager should have the shortcut registered", 1, newFakeMenuController.registeredActions.size)
        assertNotNull(newFakeMenuController.registeredActions[copyKeyStroke])
    }
}
