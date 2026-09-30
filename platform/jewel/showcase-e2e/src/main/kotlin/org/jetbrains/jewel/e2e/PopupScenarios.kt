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

    /** The popup content a ComboBox shows, set by the component itself rather than by either sample. */
    public const val COMBO_BOX_POPUP: String = "Jewel.ComboBox.Popup"
}

/**
 * Popup behaviours that must hold identically in the standalone sample and in the IDE. The first scenario group; others
 * sit beside it, one object per area of the showcase.
 *
 * Each returns `null` when the behaviour held, or a description of what went wrong, so a caller can run several and
 * report all the failures rather than stopping at the first.
 */
public object PopupScenarios {
    /** Opens the combo boxes view and runs every scenario of this group, each isolated from the next. */
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
}
