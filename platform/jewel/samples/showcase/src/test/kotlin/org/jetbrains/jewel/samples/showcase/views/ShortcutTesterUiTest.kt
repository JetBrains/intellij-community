// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the
// Apache 2.0 license.
package org.jetbrains.jewel.samples.showcase.views

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.remember
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.requestFocus
import androidx.compose.ui.test.withKeyDown
import org.jetbrains.jewel.foundation.shortcut.DefaultJewelActionRegistry
import org.jetbrains.jewel.foundation.shortcut.InMemoryJewelKeymap
import org.jetbrains.jewel.foundation.shortcut.JewelShortcutActions
import org.jetbrains.jewel.foundation.shortcut.JewelShortcutHostState
import org.jetbrains.jewel.foundation.shortcut.ProvideJewelShortcutHost
import org.jetbrains.jewel.intui.standalone.theme.IntUiTheme
import org.jetbrains.jewel.samples.showcase.ShowcaseShortcuts
import org.junit.Rule
import org.junit.Test

/**
 * How an application tests its shortcuts — the pattern this repo recommends: compose the real UI under a shortcut host,
 * drive it with idiomatic scene key injection ([performKeyInput]), and assert on semantics. No window, no AWT hooks, no
 * IDE fixture.
 */
public class ShortcutTesterUiTest {
    @get:Rule public val rule: ComposeContentTestRule = createComposeRule()

    private fun setTesterContent() {
        rule.setContent {
            IntUiTheme {
                // Deterministic non-mac modifiers in tests: DemoSelectAll = Ctrl+A, DemoPing = Alt+G.
                val registry = remember {
                    DefaultJewelActionRegistry().also { registry ->
                        ShowcaseShortcuts.definitions(useMacModifiers = false).forEach(registry::register)
                    }
                }
                val keymap = remember { InMemoryJewelKeymap.fromDefaults("test", registry) }
                val host = remember { JewelShortcutHostState(registry) { keymap } }
                ProvideJewelShortcutHost(host) { Box(host.resolverRootModifier) { ShortcutTesterView() } }
            }
        }
    }

    @Test
    public fun `keymap command dispatches to the focused card and the event log records it`() {
        setTesterContent()

        rule.onNodeWithTag("ShortcutTester.Outer").requestFocus()
        rule.onNodeWithTag("ShortcutTester.Outer").performKeyInput { withKeyDown(Key.CtrlLeft) { pressKey(Key.A) } }
        rule.waitForIdle()

        rule.onNodeWithText("Outer surface binding — fired 1 times", substring = true).assertExists()
    }

    @Test
    public fun `nested override wins while the inner card is focused`() {
        setTesterContent()

        rule.onNodeWithTag("ShortcutTester.Editor").requestFocus()
        rule.onNodeWithTag("ShortcutTester.Editor").performKeyInput { withKeyDown(Key.CtrlLeft) { pressKey(Key.A) } }
        rule.waitForIdle()

        rule.onNodeWithText("Inner 'editor' override — fired 1 times", substring = true).assertExists()
        rule.onNodeWithText("Outer surface binding — fired 0 times", substring = true).assertExists()
    }

    @Test
    public fun `focused claim vetoes the keymap command bound to the same stroke`() {
        setTesterContent()

        // Focused on the action card, Alt+G fires the Ping action.
        rule.onNodeWithTag("ShortcutTester.Ping").requestFocus()
        rule.onNodeWithTag("ShortcutTester.Ping").performKeyInput { withKeyDown(Key.AltLeft) { pressKey(Key.G) } }
        rule.waitForIdle()
        rule.onNodeWithText("Ping action — fired 1 times", substring = true).assertExists()

        // Focused on the claiming card, the same stroke is owned locally: Ping stays at 1.
        rule.onNodeWithTag("ShortcutTester.Claimer").requestFocus()
        rule.onNodeWithTag("ShortcutTester.Claimer").performKeyInput { withKeyDown(Key.AltLeft) { pressKey(Key.G) } }
        rule.waitForIdle()
        rule.onNodeWithText("claimed 1 times", substring = true).assertExists()
        rule.onNodeWithText("Ping action — fired 1 times", substring = true).assertExists()
    }

    @Test
    public fun `bindings are discoverable through semantics`() {
        setTesterContent()

        rule
            .onNodeWithTag("ShortcutTester.Outer")
            .assert(
                SemanticsMatcher.expectValue(JewelShortcutActions, listOf(ShowcaseShortcuts.DemoSelectAll.id.value))
            )
    }
}
