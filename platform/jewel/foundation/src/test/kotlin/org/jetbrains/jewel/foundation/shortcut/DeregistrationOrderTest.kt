// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jewel.foundation.shortcut

import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.key
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.withKeyDown
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test

/**
 * Regression coverage for the deregistration-order bug in [ShortcutResolverRootNode].
 *
 * Focused participant nodes register innermost-first — the focus system dispatches focus events from the active target
 * upward — so a node's backing list ends up in reverse-depth (NOT depth) order. The root re-sorts each published
 * snapshot outermost-first on *every* registration change; the engine then treats the LAST snapshot entry as innermost.
 * A deregistration that merely copied the backing list would republish it in insertion order and make the engine
 * resolve an OUTER node as the innermost one — silently (no error/log), and only until the next registration re-sorted
 * it.
 *
 * Each test registers two same-action (or same-sequence/same-matcher) participants at different nesting depths, then
 * deregisters an unrelated same-kind participant (a tail modifier on the root that leaves the composition), and asserts
 * innermost-wins still holds for dispatch, the `blocksOuterBindings` block, claim precedence, and raw-claim
 * nearest-first. Before the fix each assertion fails because the snapshot reverts to the inner-first insertion order.
 */
internal class DeregistrationOrderTest {
    @JvmField @Rule internal val rule: ComposeContentTestRule = createComposeRule()

    private val action = JewelAction(JewelActionId("test.dereg.action"), "Action")
    private val unrelated = JewelAction(JewelActionId("test.dereg.unrelated"), "Unrelated")
    private val keymap = InMemoryJewelKeymap("dereg-test")
    private val host = JewelShortcutHostState { keymap }

    private val innerFocus = FocusRequester()

    private fun focusInner() {
        runBlocking {
            rule.runOnIdle { innerFocus.requestFocus() }
            rule.awaitIdle()
        }
    }

    @Test
    fun `innermost binding still wins after an unrelated binding deregisters`() {
        var innerInvocations = 0
        var outerInvocations = 0
        var extraBound by mutableStateOf(true)

        rule.setContent {
            Box(host.resolverRootModifier.then(if (extraBound) Modifier.shortcut(unrelated) {} else Modifier)) {
                Box(Modifier.shortcut(action) { outerInvocations++ }) {
                    Box(
                        Modifier.testTag("inner")
                            .shortcut(action) { innerInvocations++ }
                            .focusRequester(innerFocus)
                            .focusable()
                    )
                }
            }
        }
        focusInner()

        // The unrelated binding registered alongside the nested pair; removing it deregisters a same-kind node and
        // rebuilds the binding snapshot — the step that used to revert the pair to insertion order.
        rule.runOnIdle { extraBound = false }
        rule.waitForIdle()

        rule.runOnIdle { host.resolveFocusedHandler(action.id)?.invoke() }
        assertEquals("the innermost binding must resolve after a deregistration", 1, innerInvocations)
        assertEquals("the outer binding must not be treated as innermost", 0, outerInvocations)
    }

    @Test
    fun `a disabled blocking inner binding still blocks the outer one after an unrelated binding deregisters`() {
        var extraBound by mutableStateOf(true)

        rule.setContent {
            Box(host.resolverRootModifier.then(if (extraBound) Modifier.shortcut(unrelated) {} else Modifier)) {
                Box(Modifier.shortcut(action) {}) { // enabled outer binding
                    Box(
                        Modifier.testTag("inner")
                            .shortcut(action, enabled = false, blocksOuterBindings = true) {}
                            .focusRequester(innerFocus)
                            .focusable()
                    )
                }
            }
        }
        focusInner()

        rule.runOnIdle { extraBound = false }
        rule.waitForIdle()

        // The innermost binding is disabled and blocks fall-through, so the action must resolve to nothing. Were the
        // snapshot reverted to insertion order, the enabled outer binding would be treated as innermost and resolve.
        rule.runOnIdle {
            assertNull(
                "a disabled blocking innermost binding must keep the action unresolved",
                host.resolveFocusedHandler(action.id),
            )
        }
    }

    @Test
    fun `innermost claim still wins after an unrelated claim deregisters`() {
        var innerClaims = 0
        var outerClaims = 0
        var extraBound by mutableStateOf(true)
        val claimed = JewelKeySequence(JewelKeyStroke(Key.M, ctrl = true))
        val unrelatedSeq = JewelKeySequence(JewelKeyStroke(Key.J, ctrl = true))

        rule.setContent {
            Box(host.resolverRootModifier.then(if (extraBound) Modifier.claimShortcut(unrelatedSeq) {} else Modifier)) {
                Box(Modifier.claimShortcut(claimed) { outerClaims++ }) {
                    Box(
                        Modifier.testTag("inner")
                            .claimShortcut(claimed) { innerClaims++ }
                            .focusRequester(innerFocus)
                            .focusable()
                    )
                }
            }
        }
        focusInner()

        rule.runOnIdle { extraBound = false }
        rule.waitForIdle()

        rule.onNodeWithTag("inner").performKeyInput { withKeyDown(Key.CtrlLeft) { pressKey(Key.M) } }
        rule.waitForIdle()
        assertEquals("the innermost claim must win after a deregistration", 1, innerClaims)
        assertEquals("the outer claim must not be treated as innermost", 0, outerClaims)
    }

    @Test
    fun `innermost raw claim still wins after an unrelated raw claim deregisters`() {
        var innerRaw = 0
        var outerRaw = 0
        var extraBound by mutableStateOf(true)

        rule.setContent {
            Box(
                host.resolverRootModifier.then(
                    if (extraBound) Modifier.claimKeyEvent(matcher = { it.key == Key.F2 }) {} else Modifier
                )
            ) {
                Box(Modifier.claimKeyEvent(matcher = { it.key == Key.F1 }) { outerRaw++ }) {
                    Box(
                        Modifier.testTag("inner")
                            .claimKeyEvent(matcher = { it.key == Key.F1 }) { innerRaw++ }
                            .focusRequester(innerFocus)
                            .focusable()
                    )
                }
            }
        }
        focusInner()

        rule.runOnIdle { extraBound = false }
        rule.waitForIdle()

        rule.onNodeWithTag("inner").performKeyInput { pressKey(Key.F1) }
        rule.waitForIdle()
        assertEquals("the innermost raw claim must win after a deregistration", 1, innerRaw)
        assertEquals("the outer raw claim must not be treated as innermost", 0, outerRaw)
    }
}
