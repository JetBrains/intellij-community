// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jewel.e2e

import java.awt.event.KeyEvent

/**
 * Test tags the scenarios address. Both hosts must expose the same ones for a scenario to run in both.
 *
 * [COMBO_BOX] intentionally repeats the literal from `ShowcaseTestTags.STRING_LIST_COMBO_BOX` rather than importing it:
 * this module depends on neither host, so that the standalone lane's runtime closure stays free of anything but Jewel.
 * `ShowcaseTestTags` is the definition; if it changes, this has to change with it, and the lanes fail loudly with the
 * visible tags listed when it does not.
 */
public object ShowcaseE2eTags {
    /** The combo box a scenario opens. Must match `ShowcaseTestTags.STRING_LIST_COMBO_BOX`. */
    public const val COMBO_BOX: String = "showcase.comboBox.stringList"

    /** The menu combo box with a selected item. Must match `ShowcaseTestTags.SIMPLE_MENU_COMBO_BOX`. */
    public const val SIMPLE_MENU_COMBO_BOX: String = "showcase.comboBox.simpleMenu"

    /** The popup content a ComboBox shows, set by the component itself rather than by either sample. */
    public const val COMBO_BOX_POPUP: String = "Jewel.ComboBox.Popup"

    /** The button that opens the basic popup menu. Must match `ShowcaseTestTags.BASIC_MENU_BUTTON`. */
    public const val BASIC_MENU_BUTTON: String = "showcase.menu.basicButton"

    /** The basic popup menu container. Must match `ShowcaseTestTags.BASIC_MENU_POPUP`. */
    public const val BASIC_MENU_POPUP: String = "showcase.menu.basicPopup"

    /** The button that opens the submenu popup menu. Must match `ShowcaseTestTags.SUBMENU_MENU_BUTTON`. */
    public const val SUBMENU_MENU_BUTTON: String = "showcase.menu.submenuButton"

    /** The submenu popup menu container. Must match `ShowcaseTestTags.SUBMENU_MENU_POPUP`. */
    public const val SUBMENU_MENU_POPUP: String = "showcase.menu.submenuPopup"
}

/**
 * Popup behaviours that must hold identically in the standalone sample and in the IDE. The first scenario group; others
 * sit beside it, one object per area of the showcase.
 *
 * Each returns `null` when the behaviour held, or a description of what went wrong, so a caller can run several and
 * report all the failures rather than stopping at the first.
 */
public object PopupScenarios {
    /** Opens the combo boxes and menus views and runs every scenario of this group, each isolated from the next. */
    public suspend fun runAll(automator: ShowcaseAutomator, failures: MutableList<String>) {
        automator.clickByContentDescription("Show Combo Boxes")
        automator.waitForNode(ShowcaseE2eTags.COMBO_BOX)
        automator.runIsolated(
            failures,
            "Escape closes a hovered ComboBox",
            ::closeLeftoverPopup,
            ::escapeClosesHoveredComboBox,
        )
        automator.runIsolated(
            failures,
            "A ComboBox popup closes cleanly",
            ::closeLeftoverPopup,
            ::comboBoxPopupClosesCleanly,
        )
        automator.runIsolated(
            failures,
            "Clicking an open ComboBox closes it without reopening",
            ::closeLeftoverPopup,
            ::clickingOpenComboBoxClosesWithoutReopening,
        )
        automator.runIsolated(
            failures,
            "A MenuComboBox with a selected item stays open and closes on click or Escape",
            ::closeLeftoverMenuComboBoxPopup,
            ::menuComboBoxWithSelectedItemStaysOpenAndCloses,
        )

        automator.clickByContentDescription("Show Menus")
        automator.waitForNode(ShowcaseE2eTags.BASIC_MENU_BUTTON)
        automator.runIsolated(
            failures,
            "Escape closes a basic PopupMenu",
            ::closeLeftoverBasicMenuPopup,
            ::basicPopupMenuOpensAndClosesWithEscape,
        )
        automator.runIsolated(
            failures,
            "Keyboard navigation selects an item and closes a basic PopupMenu",
            ::closeLeftoverBasicMenuPopup,
            ::basicPopupMenuKeyboardNavigationSelectsItem,
        )
        automator.runIsolated(
            failures,
            "Keyboard navigation opens and closes nested Submenus in PopupMenu",
            ::closeLeftoverSubmenuPopup,
            ::submenuPopupMenuRoutesKeysAndCloses,
        )
    }

    /**
     * What [runIsolated] runs after each of these scenarios: closes a leftover popup by clicking its owner, not by
     * pressing Escape, which is inert under precisely the fault these scenarios exist to catch.
     */
    public suspend fun closeLeftoverPopup(automator: ShowcaseAutomator) {
        if (automator.isPresent(ShowcaseE2eTags.COMBO_BOX_POPUP)) {
            automator.click(ShowcaseE2eTags.COMBO_BOX)
            automator.waitUntilGone(ShowcaseE2eTags.COMBO_BOX_POPUP)
        }
    }

    public suspend fun closeLeftoverMenuComboBoxPopup(automator: ShowcaseAutomator) {
        if (automator.isPresent(ShowcaseE2eTags.COMBO_BOX_POPUP)) {
            automator.click(ShowcaseE2eTags.SIMPLE_MENU_COMBO_BOX)
            automator.waitUntilGone(ShowcaseE2eTags.COMBO_BOX_POPUP)
        }
    }

    public suspend fun closeLeftoverBasicMenuPopup(automator: ShowcaseAutomator) {
        if (automator.isPresent(ShowcaseE2eTags.BASIC_MENU_POPUP)) {
            automator.clickByContentDescription("Show Menus")
            automator.waitUntilGone(ShowcaseE2eTags.BASIC_MENU_POPUP)
        }
    }

    public suspend fun closeLeftoverSubmenuPopup(automator: ShowcaseAutomator) {
        if (automator.isPresent(ShowcaseE2eTags.SUBMENU_MENU_POPUP)) {
            automator.clickByContentDescription("Show Menus")
            automator.waitUntilGone(ShowcaseE2eTags.SUBMENU_MENU_POPUP)
        }
    }

    /**
     * Escape must close a ComboBox popup while the pointer is still over the ComboBox.
     *
     * This is the JEWEL-1396 fault. A renderer that claims the key without acting on it starves the ComboBox's own key
     * handler, and the popup stays open with the key swallowed on the way. It only ever reproduced against the IDE's
     * `JBPopupRenderer`: the standalone renderer's window-ownership check makes non-focusable popups decline the key
     * before the question arises, which is exactly why running this scenario in one host is not enough.
     */
    public suspend fun escapeClosesHoveredComboBox(automator: ShowcaseAutomator): String? {
        automator.click(ShowcaseE2eTags.COMBO_BOX)
        automator.waitForNode(ShowcaseE2eTags.COMBO_BOX_POPUP)

        // The pointer stays where the click landed, which is what keeps the ComboBox hovered.
        automator.pressKey(KeyEvent.VK_ESCAPE)

        return runCatching { automator.waitUntilGone(ShowcaseE2eTags.COMBO_BOX_POPUP) }
            .fold(
                onSuccess = { null },
                onFailure = { "the popup stayed open, so the renderer claimed Escape without acting on it" },
            )
    }

    /**
     * Opening and closing a ComboBox popup must leave nothing behind.
     *
     * Cheap on its own, but it is what makes running several scenarios in one session trustworthy: a leaked popup would
     * make whichever scenario ran next fail for the wrong reason.
     */
    public suspend fun comboBoxPopupClosesCleanly(automator: ShowcaseAutomator): String? {
        automator.click(ShowcaseE2eTags.COMBO_BOX)
        automator.waitForNode(ShowcaseE2eTags.COMBO_BOX_POPUP)
        automator.pressKey(KeyEvent.VK_ESCAPE)
        // Kept rather than discarded: a popup that closes only after the wait budget still leaves nothing
        // behind by the time the check below runs, so swallowing this would hide a real regression.
        val closedInTime = runCatching { automator.waitUntilGone(ShowcaseE2eTags.COMBO_BOX_POPUP) }.isSuccess

        automator.waitForIdle()
        return when {
            automator.isPresent(ShowcaseE2eTags.COMBO_BOX_POPUP) -> "a popup was still on screen after Escape"
            !closedInTime -> "the popup did close, but took longer than the wait allows"
            else -> null
        }
    }

    /**
     * Clicking an open ComboBox while it is hovered must close the popup without immediately reopening it.
     */
    public suspend fun clickingOpenComboBoxClosesWithoutReopening(automator: ShowcaseAutomator): String? {
        automator.click(ShowcaseE2eTags.COMBO_BOX)
        automator.waitForNode(ShowcaseE2eTags.COMBO_BOX_POPUP)
        automator.waitForIdle()

        automator.click(ShowcaseE2eTags.COMBO_BOX)
        val closedInTime = runCatching { automator.waitUntilGone(ShowcaseE2eTags.COMBO_BOX_POPUP) }.isSuccess
        automator.waitForIdle()

        return when {
            automator.isPresent(ShowcaseE2eTags.COMBO_BOX_POPUP) ->
                "clicking the open ComboBox left the popup open or reopened it"
            !closedInTime -> "the ComboBox popup took longer than the wait budget to close on second click"
            else -> null
        }
    }

    /**
     * A MenuComboBox whose menu has a selected item must stay open when clicked, accept keyboard navigation, close
     * without reopening when clicked again, and close on Escape when reopened.
     *
     * When the selected menu item requests Compose focus during pre-measurement or popup show, stealing focus from the
     * anchor ComboBox causes `ComboBox` to close its popup on the same click that opened it.
     */
    public suspend fun menuComboBoxWithSelectedItemStaysOpenAndCloses(automator: ShowcaseAutomator): String? {
        automator.click(ShowcaseE2eTags.SIMPLE_MENU_COMBO_BOX)
        automator.waitForNode(ShowcaseE2eTags.COMBO_BOX_POPUP)
        automator.waitForIdle()
        if (!automator.isPresent(ShowcaseE2eTags.COMBO_BOX_POPUP)) {
            return "the MenuComboBox popup closed itself immediately after opening"
        }

        automator.pressKey(KeyEvent.VK_DOWN)
        automator.waitForIdle()
        if (!automator.isPresent(ShowcaseE2eTags.COMBO_BOX_POPUP)) {
            return "the MenuComboBox popup closed when pressing Down arrow"
        }

        automator.click(ShowcaseE2eTags.SIMPLE_MENU_COMBO_BOX)
        val closedOnSecondClick = runCatching { automator.waitUntilGone(ShowcaseE2eTags.COMBO_BOX_POPUP) }.isSuccess
        automator.waitForIdle()
        if (!closedOnSecondClick || automator.isPresent(ShowcaseE2eTags.COMBO_BOX_POPUP)) {
            return "clicking the open MenuComboBox did not close its popup cleanly"
        }

        automator.click(ShowcaseE2eTags.SIMPLE_MENU_COMBO_BOX)
        automator.waitForNode(ShowcaseE2eTags.COMBO_BOX_POPUP)
        automator.waitForIdle()

        automator.pressKey(KeyEvent.VK_ESCAPE)
        return runCatching { automator.waitUntilGone(ShowcaseE2eTags.COMBO_BOX_POPUP) }
            .fold(
                onSuccess = { null },
                onFailure = { "the MenuComboBox popup stayed open after Escape" },
            )
    }

    /**
     * A focusable PopupMenu must open on button click and close when Escape is pressed while the owner window holds
     * native focus.
     */
    public suspend fun basicPopupMenuOpensAndClosesWithEscape(automator: ShowcaseAutomator): String? {
        automator.click(ShowcaseE2eTags.BASIC_MENU_BUTTON)
        automator.waitForNode(ShowcaseE2eTags.BASIC_MENU_POPUP)
        automator.waitForIdle()

        automator.pressKey(KeyEvent.VK_ESCAPE)
        return runCatching { automator.waitUntilGone(ShowcaseE2eTags.BASIC_MENU_POPUP) }
            .fold(
                onSuccess = { null },
                onFailure = { "the basic PopupMenu stayed open after Escape" },
            )
    }

    /**
     * Pressing Down arrow and Space in a focusable PopupMenu must focus an item in the popup's own scene, activate it,
     * and close the menu.
     */
    public suspend fun basicPopupMenuKeyboardNavigationSelectsItem(automator: ShowcaseAutomator): String? {
        automator.click(ShowcaseE2eTags.BASIC_MENU_BUTTON)
        automator.waitForNode(ShowcaseE2eTags.BASIC_MENU_POPUP)
        automator.waitForIdle()

        automator.pressKey(KeyEvent.VK_DOWN)
        automator.waitForIdle()
        automator.pressKey(KeyEvent.VK_SPACE)

        return runCatching { automator.waitUntilGone(ShowcaseE2eTags.BASIC_MENU_POPUP) }
            .fold(
                onSuccess = { null },
                onFailure = { "pressing Down and Space in the basic PopupMenu did not select an item and close it" },
            )
    }

    /**
     * Keyboard navigation in a PopupMenu with nested submenus must route keys to the innermost active submenu and close
     * each level cleanly.
     */
    public suspend fun submenuPopupMenuRoutesKeysAndCloses(automator: ShowcaseAutomator): String? {
        automator.click(ShowcaseE2eTags.SUBMENU_MENU_BUTTON)
        automator.waitForNode(ShowcaseE2eTags.SUBMENU_MENU_POPUP)
        automator.waitForIdle()

        // Navigate down to "More Options" and press Right to open the first-level submenu.
        automator.pressKey(KeyEvent.VK_DOWN)
        automator.waitForIdle()
        automator.pressKey(KeyEvent.VK_DOWN)
        automator.waitForIdle()
        automator.pressKey(KeyEvent.VK_RIGHT)
        automator.waitForIdle()

        // Navigate down to "Nested Submenu" (3rd item in first submenu) and press Right to open the nested submenu.
        automator.pressKey(KeyEvent.VK_DOWN)
        automator.waitForIdle()
        automator.pressKey(KeyEvent.VK_DOWN)
        automator.waitForIdle()
        automator.pressKey(KeyEvent.VK_DOWN)
        automator.waitForIdle()
        automator.pressKey(KeyEvent.VK_RIGHT)
        automator.waitForIdle()

        // First Left closes only the nested submenu.
        automator.pressKey(KeyEvent.VK_LEFT)
        automator.waitForIdle()
        if (!automator.isPresent(ShowcaseE2eTags.SUBMENU_MENU_POPUP)) {
            return "pressing Left in a nested submenu closed the root PopupMenu"
        }

        // Second Left closes the first-level submenu, leaving the root PopupMenu open.
        automator.pressKey(KeyEvent.VK_LEFT)
        automator.waitForIdle()
        if (!automator.isPresent(ShowcaseE2eTags.SUBMENU_MENU_POPUP)) {
            return "pressing Left in the first submenu closed the root PopupMenu instead of only the submenu"
        }

        // Escape closes the root PopupMenu.
        automator.pressKey(KeyEvent.VK_ESCAPE)
        return runCatching { automator.waitUntilGone(ShowcaseE2eTags.SUBMENU_MENU_POPUP) }
            .fold(
                onSuccess = { null },
                onFailure = { "the submenu PopupMenu stayed open after Escape" },
            )
    }
}
