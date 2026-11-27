// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jewel.e2e

import java.awt.event.InputEvent
import java.awt.event.KeyEvent

/**
 * Shortcut claim-veto and typed-suppression scenarios that must hold identically in the standalone sample and in the
 * IDE bridge.
 */
public object ShortcutScenarios {
    /** Opens the Shortcuts tester view and runs every scenario of this group, each isolated from the next. */
    public suspend fun runAll(automator: ShowcaseAutomator, failures: MutableList<String>) {
        automator.clickByContentDescription("Show Shortcuts")
        automator.waitForNode(ShowcaseE2eTags.SHORTCUT_TESTER_CLAIMER)
        automator.waitForIdle()
        automator.runIsolated(
            failures,
            "Focused claim vetoes host keymap dispatch and delivers locally",
            ::restoreClaimCheckbox,
            ::focusedClaimVetoesAndDelivers,
        )
        automator.runIsolated(
            failures,
            "Claimed printable key suppresses KEY_TYPED in a TextField",
            ::noOpCleanup,
            ::claimedPrintableKeySuppressesKeyTypedInTextField,
        )
    }

    private suspend fun restoreClaimCheckbox(automator: ShowcaseAutomator) {
        if (!automator.isSelected(ShowcaseE2eTags.SHORTCUT_TESTER_CLAIM_ACTIVE)) {
            automator.click(ShowcaseE2eTags.SHORTCUT_TESTER_CLAIM_ACTIVE)
            automator.waitForIdle()
        }
    }

    private suspend fun noOpCleanup(@Suppress("UNUSED_PARAMETER") automator: ShowcaseAutomator) = Unit

    public suspend fun focusedClaimVetoesAndDelivers(automator: ShowcaseAutomator): String? {
        automator.click(ShowcaseE2eTags.SHORTCUT_TESTER_CLAIMER)
        automator.waitForIdle()

        automator.pressKey(KeyEvent.VK_G, InputEvent.ALT_DOWN_MASK)
        automator.waitUntil("the claiming surface counter to reach 1") {
            automator.textOf(ShowcaseE2eTags.SHORTCUT_TESTER_CLAIMER_COUNTER)?.contains("claimed 1 times") == true
        }

        val pingText = automator.textOf(ShowcaseE2eTags.SHORTCUT_TESTER_PING_COUNTER)
        if (pingText?.contains("fired 0 times") != true) {
            return "expected Ping counter to remain at 0 while Claimer held focus, got '$pingText'"
        }

        // Disable the claim, re-focus Claimer, and verify the disabled claim does not deliver while Ping fires.
        automator.click(ShowcaseE2eTags.SHORTCUT_TESTER_CLAIM_ACTIVE)
        automator.waitForIdle()
        automator.click(ShowcaseE2eTags.SHORTCUT_TESTER_CLAIMER)
        automator.waitForIdle()
        automator.pressKey(KeyEvent.VK_G, InputEvent.ALT_DOWN_MASK)
        automator.waitUntil("Ping counter to reach 1 after disabling the claim") {
            automator.textOf(ShowcaseE2eTags.SHORTCUT_TESTER_PING_COUNTER)?.contains("fired 1 times") == true
        }

        val claimerAfterDisable = automator.textOf(ShowcaseE2eTags.SHORTCUT_TESTER_CLAIMER_COUNTER)
        if (claimerAfterDisable?.contains("claimed 1 times") != true) {
            return "expected disabled claim to stay at 1, got '$claimerAfterDisable'"
        }
        return null
    }

    public suspend fun claimedPrintableKeySuppressesKeyTypedInTextField(automator: ShowcaseAutomator): String? {
        automator.click(ShowcaseE2eTags.SHORTCUT_TESTER_TEXT_FIELD)
        automator.waitForIdle()

        // Unshifted 'k' matches JewelKeyStroke(Key.K), runs the macro, and suppresses its KEY_TYPED event.
        automator.typeText("hekllo")
        automator.waitUntil("the text field to contain 'hello' and macro count to reach 1") {
            automator.editableTextOf(ShowcaseE2eTags.SHORTCUT_TESTER_TEXT_FIELD) == "hello" &&
                automator.textOf(ShowcaseE2eTags.SHORTCUT_TESTER_MACRO_COUNTER)?.contains("Macro recorded 1 times") ==
                    true
        }

        automator.pressKey(KeyEvent.VK_K)
        automator.waitUntil("the macro count to reach 2 while keeping text 'hello'") {
            automator.editableTextOf(ShowcaseE2eTags.SHORTCUT_TESTER_TEXT_FIELD) == "hello" &&
                automator.textOf(ShowcaseE2eTags.SHORTCUT_TESTER_MACRO_COUNTER)?.contains("Macro recorded 2 times") ==
                    true
        }

        return null
    }
}
