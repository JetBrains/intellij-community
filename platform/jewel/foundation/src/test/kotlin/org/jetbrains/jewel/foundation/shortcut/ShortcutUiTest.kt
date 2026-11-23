// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jewel.foundation.shortcut

import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.withKeyDown
import androidx.compose.ui.unit.dp
import java.awt.Panel
import java.awt.event.InputEvent
import java.awt.event.KeyEvent as AwtKeyEvent
import kotlinx.coroutines.runBlocking
import org.jetbrains.jewel.foundation.JewelFlags
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * The UI-test story for shortcuts: a plain Compose UI test can drive dispatch with idiomatic scene key injection
 * ([performKeyInput]) — no window, no AWT hook, no manual host calls — because the resolver root participates in the
 * scene's key-event preview pass. Bindings and claims are also discoverable through semantics ([JewelShortcutActions],
 * [JewelClaimedShortcuts]).
 */
internal class ShortcutUiTest {
    @JvmField @Rule internal val rule: ComposeContentTestRule = createComposeRule()

    private val selectAll = JewelAction(JewelActionId("test.ui.selectAll"), "Select All Rows")
    private val keymap =
        InMemoryJewelKeymap("ui-test").apply {
            bind(selectAll.id, JewelKeySequence(JewelKeyStroke(Key.S, ctrl = true)))
        }
    private val host = JewelShortcutHostState { keymap }

    private var bindingInvocations = 0
    private var claimInvocations = 0
    private var keysSeenByFocusedChild = 0

    private val boundFocus = FocusRequester()
    private val claimerFocus = FocusRequester()

    @Composable
    private fun Content() {
        Box(host.resolverRootModifier) {
            Column {
                Box(
                    Modifier.testTag("bound")
                        .shortcut(selectAll) { bindingInvocations++ }
                        .focusRequester(boundFocus)
                        .focusable()
                )
                Box(
                    Modifier.testTag("claimer")
                        .claimShortcut(JewelKeySequence(JewelKeyStroke(Key.M, ctrl = true))) { claimInvocations++ }
                        .focusRequester(claimerFocus)
                        .focusable()
                        .onKeyEvent {
                            keysSeenByFocusedChild++
                            false
                        }
                )
            }
        }
    }

    private fun focus(requester: FocusRequester) {
        runBlocking {
            rule.runOnIdle { requester.requestFocus() }
            rule.awaitIdle()
        }
    }

    @Test
    fun `keymap command dispatches from injected scene input while its binding is focused`() {
        rule.setContent { Content() }
        focus(boundFocus)

        rule.onNodeWithTag("bound").performKeyInput { withKeyDown(Key.CtrlLeft) { pressKey(Key.S) } }
        rule.waitForIdle()
        assertEquals(1, bindingInvocations)

        // Unfocused binding: same injection at the other node must not dispatch.
        focus(claimerFocus)
        rule.onNodeWithTag("claimer").performKeyInput { withKeyDown(Key.CtrlLeft) { pressKey(Key.S) } }
        rule.waitForIdle()
        assertEquals(1, bindingInvocations)
    }

    @Test
    fun `focused claim consumes injected input before ordinary key handling`() {
        rule.setContent { Content() }
        focus(claimerFocus)

        rule.onNodeWithTag("claimer").performKeyInput { withKeyDown(Key.CtrlLeft) { pressKey(Key.M) } }
        rule.waitForIdle()
        assertEquals(1, claimInvocations)
        // Consumed on the preview pass at the resolver root: the focused child's own key handling
        // never saw the claimed stroke (only the Ctrl presses/releases at most).
        val seenAfterClaim = keysSeenByFocusedChild

        // An unbound key falls through to ordinary input and DOES reach the focused child.
        rule.onNodeWithTag("claimer").performKeyInput { pressKey(Key.B) }
        rule.waitForIdle()
        assertEquals(1, claimInvocations)
        assertEquals(seenAfterClaim + 2, keysSeenByFocusedChild) // key-down + key-up of B
    }

    @Test
    fun `bindings and claims are discoverable through semantics`() {
        rule.setContent { Content() }

        rule
            .onNodeWithTag("bound")
            .assert(SemanticsMatcher.expectValue(JewelShortcutActions, listOf(selectAll.id.value)))
        // The claimed sequence is surfaced through its OS-appropriate display text (⌃M on macOS, Ctrl+M elsewhere),
        // so assert against the same rendering the production path uses rather than a platform-specific literal.
        val claimedDisplayText = JewelKeySequence(JewelKeyStroke(Key.M, ctrl = true)).displayText()
        rule
            .onNodeWithTag("claimer")
            .assert(SemanticsMatcher.expectValue(JewelClaimedShortcuts, listOf(claimedDisplayText)))
    }

    @Test
    fun `armed chord resets when focus leaves the resolver root`() {
        val reformat = JewelAction(JewelActionId("test.ui.reformat"), "Reformat")
        val chordKeymap =
            InMemoryJewelKeymap("ui-test-chord").apply {
                bind(
                    reformat.id,
                    JewelKeySequence(JewelKeyStroke(Key.K, ctrl = true), JewelKeyStroke(Key.D, ctrl = true)),
                )
            }
        val chordHost = JewelShortcutHostState { chordKeymap }
        var reformats = 0
        val outsideFocus = FocusRequester()

        rule.setContent {
            Column {
                Box(chordHost.resolverRootModifier) {
                    Box(
                        Modifier.testTag("chordTarget")
                            .shortcut(reformat) { reformats++ }
                            .focusRequester(boundFocus)
                            .focusable()
                    )
                }
                // A focus target OUTSIDE the resolver root: moving here is "focus leaves the surface".
                Box(Modifier.testTag("outside").focusRequester(outsideFocus).focusable())
            }
        }

        focus(boundFocus)
        rule.onNodeWithTag("chordTarget").performKeyInput { withKeyDown(Key.CtrlLeft) { pressKey(Key.K) } }
        rule.runOnIdle { assertTrue(chordHost.isAwaitingSecondStroke) }

        // Focus round-trip: the armed chord must not survive it.
        focus(outsideFocus)
        rule.runOnIdle { assertFalse(chordHost.isAwaitingSecondStroke) }
        focus(boundFocus)

        rule.onNodeWithTag("chordTarget").performKeyInput { withKeyDown(Key.CtrlLeft) { pressKey(Key.D) } }
        rule.waitForIdle()
        assertEquals(0, reformats)

        // And the full chord still works after the reset.
        rule.onNodeWithTag("chordTarget").performKeyInput {
            withKeyDown(Key.CtrlLeft) { pressKey(Key.K) }
            withKeyDown(Key.CtrlLeft) { pressKey(Key.D) }
        }
        rule.waitForIdle()
        assertEquals(1, reformats)
    }

    @Test
    fun `claimShortcut rejects two-stroke sequences`() {
        assertThrows(IllegalArgumentException::class.java) {
            Modifier.claimShortcut(
                JewelKeySequence(JewelKeyStroke(Key.K, ctrl = true), JewelKeyStroke(Key.D, ctrl = true))
            ) {}
        }
    }

    @Test
    fun `dispatch survives a host-state swap without a focus change`() {
        // ShortcutResolverRootElement.update() swaps the state under a stable root node with no focus
        // transitions firing anywhere; registrations live on the root node precisely so they survive this.
        val keymapB =
            InMemoryJewelKeymap("ui-test-b").apply {
                bind(selectAll.id, JewelKeySequence(JewelKeyStroke(Key.S, ctrl = true)))
            }
        val hostB = JewelShortcutHostState { keymapB }
        var currentHost by mutableStateOf(host)

        rule.setContent {
            Box(currentHost.resolverRootModifier) {
                Box(
                    Modifier.testTag("bound")
                        .shortcut(selectAll) { bindingInvocations++ }
                        .focusRequester(boundFocus)
                        .focusable()
                )
            }
        }
        focus(boundFocus)

        rule.onNodeWithTag("bound").performKeyInput { withKeyDown(Key.CtrlLeft) { pressKey(Key.S) } }
        rule.waitForIdle()
        assertEquals(1, bindingInvocations)

        rule.runOnIdle { currentHost = hostB }
        rule.waitForIdle()

        rule.onNodeWithTag("bound").performKeyInput { withKeyDown(Key.CtrlLeft) { pressKey(Key.S) } }
        rule.waitForIdle()
        assertEquals(2, bindingInvocations)
    }

    @Test
    fun `strict mode makes off-thread dispatch throw`() {
        rule.setContent { Content() }
        rule.waitForIdle()

        val awtEvent =
            java.awt.event.KeyEvent(
                java.awt.Panel(),
                java.awt.event.KeyEvent.KEY_PRESSED,
                0L,
                java.awt.event.KeyEvent.CTRL_DOWN_MASK,
                java.awt.event.KeyEvent.VK_S,
                's',
            )
        val composeEvent = awtEvent.toComposeKeyEvent()

        JewelFlags.strictMode = true
        try {
            var thrown: Throwable? = null
            val thread = Thread { thrown = runCatching { host.onPreviewKeyEvent(composeEvent) }.exceptionOrNull() }
            thread.start()
            thread.join(10_000)

            assertTrue("Expected IllegalStateException, got $thrown", thrown is IllegalStateException)
            assertTrue(thrown!!.message!!.contains("UI thread"))
        } finally {
            JewelFlags.strictMode = false
        }
        assertEquals(0, bindingInvocations)
    }

    @Test
    fun `sampled presentation re-resolves when focus changes the focused binding set`() {
        // Presentation is demand-driven and cached: without invalidation on focus changes, a control bound to an
        // action would keep rendering whatever was true when it first sampled. The IJPL bridge hides this behind
        // the platform's update cadence; standalone has no other signal.
        rule.setContent { Content() }
        val sampled = host.presentations.acquire(selectAll.id)

        rule.runOnIdle { assertEquals(ActionResolution.NoFocusedBinding, sampled.value.resolution) }

        focus(boundFocus)
        rule.runOnIdle { assertEquals(ActionResolution.Resolved, sampled.value.resolution) }

        focus(claimerFocus)
        rule.runOnIdle { assertEquals(ActionResolution.NoFocusedBinding, sampled.value.resolution) }
    }

    @Test
    fun `sampled presentation re-resolves when a binding's enablement changes`() {
        val enabled = androidx.compose.runtime.mutableStateOf(true)
        rule.setContent {
            Box(host.resolverRootModifier) {
                Box(
                    Modifier.testTag("bound")
                        .shortcut(selectAll, enabled = enabled.value) { bindingInvocations++ }
                        .focusRequester(boundFocus)
                        .focusable()
                )
            }
        }
        focus(boundFocus)
        val sampled = host.presentations.acquire(selectAll.id)
        rule.runOnIdle { assertEquals(ActionResolution.Resolved, sampled.value.resolution) }

        rule.runOnIdle { enabled.value = false }
        rule.waitForIdle()
        rule.runOnIdle { assertEquals(ActionResolution.NoFocusedBinding, sampled.value.resolution) }
    }

    @Test
    fun `toggling repeatPolicy across recomposition updates binding and claim modifiers`() {
        var bindingRepeat by mutableStateOf(ShortcutRepeatPolicy.RepeatWhileHeld)
        var claimRepeat by mutableStateOf(ShortcutRepeatPolicy.RepeatWhileHeld)
        val onBinding: () -> Unit = { bindingInvocations++ }
        val onClaim: () -> Unit = { claimInvocations++ }

        rule.setContent {
            Box(host.resolverRootModifier) {
                Column {
                    Box(
                        Modifier.testTag("boundRepeat")
                            .shortcut(selectAll, repeatPolicy = bindingRepeat, onInvoke = onBinding)
                            .focusRequester(boundFocus)
                            .focusable()
                    )
                    Box(
                        Modifier.testTag("claimRepeat")
                            .claimShortcut(
                                JewelKeySequence(JewelKeyStroke(Key.M, ctrl = true)),
                                repeatPolicy = claimRepeat,
                                onInvoke = onClaim,
                            )
                            .focusRequester(claimerFocus)
                            .focusable()
                    )
                }
            }
        }

        fun keyEvent(id: Int, vk: Int) =
            java.awt.event
                .KeyEvent(
                    java.awt.Panel(),
                    id,
                    0L,
                    java.awt.event.KeyEvent.CTRL_DOWN_MASK,
                    vk,
                    java.awt.event.KeyEvent.CHAR_UNDEFINED,
                )
                .toComposeKeyEvent()

        focus(boundFocus)
        rule.runOnIdle {
            host.onPreviewKeyEvent(keyEvent(java.awt.event.KeyEvent.KEY_PRESSED, java.awt.event.KeyEvent.VK_S))
            host.onPreviewKeyEvent(keyEvent(java.awt.event.KeyEvent.KEY_PRESSED, java.awt.event.KeyEvent.VK_S))
            host.onPreviewKeyEvent(keyEvent(java.awt.event.KeyEvent.KEY_RELEASED, java.awt.event.KeyEvent.VK_S))
        }
        assertEquals(2, bindingInvocations)

        rule.runOnIdle { bindingRepeat = ShortcutRepeatPolicy.OnceUntilRelease }
        rule.waitForIdle()
        rule.runOnIdle {
            host.onPreviewKeyEvent(keyEvent(java.awt.event.KeyEvent.KEY_PRESSED, java.awt.event.KeyEvent.VK_S))
            host.onPreviewKeyEvent(keyEvent(java.awt.event.KeyEvent.KEY_PRESSED, java.awt.event.KeyEvent.VK_S))
            host.onPreviewKeyEvent(keyEvent(java.awt.event.KeyEvent.KEY_RELEASED, java.awt.event.KeyEvent.VK_S))
        }
        assertEquals(3, bindingInvocations)

        focus(claimerFocus)
        rule.runOnIdle {
            host.onPreviewKeyEvent(keyEvent(java.awt.event.KeyEvent.KEY_PRESSED, java.awt.event.KeyEvent.VK_M))
            host.onPreviewKeyEvent(keyEvent(java.awt.event.KeyEvent.KEY_PRESSED, java.awt.event.KeyEvent.VK_M))
            host.onPreviewKeyEvent(keyEvent(java.awt.event.KeyEvent.KEY_RELEASED, java.awt.event.KeyEvent.VK_M))
        }
        assertEquals(2, claimInvocations)

        rule.runOnIdle { claimRepeat = ShortcutRepeatPolicy.OnceUntilRelease }
        rule.waitForIdle()
        rule.runOnIdle {
            host.onPreviewKeyEvent(keyEvent(java.awt.event.KeyEvent.KEY_PRESSED, java.awt.event.KeyEvent.VK_M))
            host.onPreviewKeyEvent(keyEvent(java.awt.event.KeyEvent.KEY_PRESSED, java.awt.event.KeyEvent.VK_M))
            host.onPreviewKeyEvent(keyEvent(java.awt.event.KeyEvent.KEY_RELEASED, java.awt.event.KeyEvent.VK_M))
        }
        assertEquals(3, claimInvocations)
    }

    @Test
    fun `raw claim beats keymap command on same stroke, agrees with veto, and suppresses KEY_TYPED`() {
        val rawMap =
            InMemoryJewelKeymap("raw-precedence").apply {
                bind(selectAll.id, JewelKeySequence(JewelKeyStroke(Key.A, ctrl = true)))
            }
        val localHost = JewelShortcutHostState { rawMap }
        var commandHits = 0
        var rawCtrlAHits = 0
        var rawPrintableHits = 0

        rule.setContent {
            Box(localHost.resolverRootModifier) {
                Box(
                    Modifier.testTag("rawSurface")
                        .shortcut(selectAll) { commandHits++ }
                        .claimKeyEvent(matcher = { it.key == Key.A && it.isCtrlPressed }) { rawCtrlAHits++ }
                        .claimKeyEvent(matcher = { it.key == Key.K && !it.isCtrlPressed }) { rawPrintableHits++ }
                        .focusRequester(boundFocus)
                        .focusable()
                )
            }
        }
        focus(boundFocus)

        rule.runOnIdle {
            val ctrlADown =
                AwtKeyEvent(
                        Panel(),
                        AwtKeyEvent.KEY_PRESSED,
                        0L,
                        InputEvent.CTRL_DOWN_MASK,
                        AwtKeyEvent.VK_A,
                        AwtKeyEvent.CHAR_UNDEFINED,
                    )
                    .toComposeKeyEvent()
            assertTrue("veto predicate must claim Ctrl+A", localHost.claimsKeyDown(ctrlADown))
            assertTrue("delivery must consume Ctrl+A", localHost.onPreviewKeyEvent(ctrlADown))
            assertEquals(1, rawCtrlAHits)
            assertEquals("raw claim must beat keymap command", 0, commandHits)

            val kDown = AwtKeyEvent(Panel(), AwtKeyEvent.KEY_PRESSED, 0L, 0, AwtKeyEvent.VK_K, 'k').toComposeKeyEvent()
            assertTrue(localHost.claimsKeyDown(kDown))
            assertTrue(localHost.onPreviewKeyEvent(kDown))
            assertEquals(1, rawPrintableHits)

            val kTyped =
                AwtKeyEvent(Panel(), AwtKeyEvent.KEY_TYPED, 0L, 0, AwtKeyEvent.VK_UNDEFINED, 'k').toComposeKeyEvent()
            assertTrue("trailing KEY_TYPED after raw claim must be suppressed", localHost.onPreviewKeyEvent(kTyped))
        }
    }

    @Test
    fun `isAwaitingSecondStroke recomposes and clears on pointer press`() {
        val reformat = JewelAction(JewelActionId("test.ui.reformat"), "Reformat")
        val chordMap =
            InMemoryJewelKeymap("chord-ui").apply {
                bind(
                    reformat.id,
                    JewelKeySequence(JewelKeyStroke(Key.K, ctrl = true), JewelKeyStroke(Key.D, ctrl = true)),
                )
            }
        val pointerHost = JewelShortcutHostState(chordTimeoutMillis = 10_000L) { chordMap }
        var observedPending = false

        rule.setContent {
            observedPending = pointerHost.isAwaitingSecondStroke
            Box(Modifier.size(64.dp).then(pointerHost.resolverRootModifier)) {
                Box(
                    Modifier.size(64.dp)
                        .testTag("chordBox")
                        .shortcut(reformat) { bindingInvocations++ }
                        .focusRequester(boundFocus)
                        .focusable()
                )
            }
        }
        focus(boundFocus)
        assertFalse(observedPending)

        rule.onNodeWithTag("chordBox").performKeyInput { withKeyDown(Key.CtrlLeft) { pressKey(Key.K) } }
        rule.waitUntil(timeoutMillis = 2_000L) { observedPending }
        assertTrue("isAwaitingSecondStroke must recompose to true", observedPending)

        rule.onNodeWithTag("chordBox").performClick()
        rule.waitUntil(timeoutMillis = 2_000L) { !observedPending }
        assertFalse("pointer press must cancel pending chord", observedPending)
    }

    @Test
    fun `isAwaitingSecondStroke recomposes and clears on timeout`() {
        val reformat = JewelAction(JewelActionId("test.ui.reformat"), "Reformat")
        val chordMap =
            InMemoryJewelKeymap("chord-ui").apply {
                bind(
                    reformat.id,
                    JewelKeySequence(JewelKeyStroke(Key.K, ctrl = true), JewelKeyStroke(Key.D, ctrl = true)),
                )
            }
        val timedHost = JewelShortcutHostState(chordTimeoutMillis = 200L) { chordMap }
        var observedPending = false

        rule.setContent {
            observedPending = timedHost.isAwaitingSecondStroke
            Box(Modifier.size(64.dp).then(timedHost.resolverRootModifier)) {
                Box(
                    Modifier.size(64.dp)
                        .testTag("chordBox")
                        .shortcut(reformat) { bindingInvocations++ }
                        .focusRequester(boundFocus)
                        .focusable()
                )
            }
        }
        focus(boundFocus)
        assertFalse(observedPending)

        rule.onNodeWithTag("chordBox").performKeyInput { withKeyDown(Key.CtrlLeft) { pressKey(Key.K) } }
        rule.waitUntil(timeoutMillis = 2_000L) { observedPending }
        assertTrue("isAwaitingSecondStroke must recompose to true", observedPending)

        rule.waitUntil(timeoutMillis = 3_000L) { !observedPending }
        assertFalse("timeout coroutine must clear pending chord automatically", observedPending)
    }

    @Test
    fun `raw claim or open menu scope consuming a key-down cancels an armed pending chord`() {
        val reformat = JewelAction(JewelActionId("test.ui.chord.cancel"), "Reformat")
        val chordMap =
            InMemoryJewelKeymap("chord-cancel").apply {
                bind(
                    reformat.id,
                    JewelKeySequence(JewelKeyStroke(Key.K, ctrl = true), JewelKeyStroke(Key.D, ctrl = true)),
                )
            }
        val localHost = JewelShortcutHostState(chordTimeoutMillis = 0L) { chordMap }
        var reformatHits = 0
        var rawHits = 0
        var menuHits = 0

        rule.setContent {
            Box(localHost.resolverRootModifier) {
                Box(
                    Modifier.size(32.dp)
                        .shortcut(reformat) { reformatHits++ }
                        .claimKeyEvent(matcher = { it.key == Key.J && it.isCtrlPressed }) { rawHits++ }
                        .focusRequester(boundFocus)
                        .focusable()
                )
            }
        }
        focus(boundFocus)

        rule.runOnIdle {
            val ctrlK =
                AwtKeyEvent(
                        Panel(),
                        AwtKeyEvent.KEY_PRESSED,
                        0L,
                        InputEvent.CTRL_DOWN_MASK,
                        AwtKeyEvent.VK_K,
                        AwtKeyEvent.CHAR_UNDEFINED,
                    )
                    .toComposeKeyEvent()
            val ctrlJ =
                AwtKeyEvent(
                        Panel(),
                        AwtKeyEvent.KEY_PRESSED,
                        0L,
                        InputEvent.CTRL_DOWN_MASK,
                        AwtKeyEvent.VK_J,
                        AwtKeyEvent.CHAR_UNDEFINED,
                    )
                    .toComposeKeyEvent()
            val ctrlD =
                AwtKeyEvent(
                        Panel(),
                        AwtKeyEvent.KEY_PRESSED,
                        0L,
                        InputEvent.CTRL_DOWN_MASK,
                        AwtKeyEvent.VK_D,
                        AwtKeyEvent.CHAR_UNDEFINED,
                    )
                    .toComposeKeyEvent()

            // (a) Raw claim consuming Ctrl+J cancels pending Ctrl+K chord.
            assertTrue(localHost.onPreviewKeyEvent(ctrlK))
            assertTrue(localHost.isAwaitingSecondStroke)
            assertTrue(localHost.onPreviewKeyEvent(ctrlJ))
            assertEquals(1, rawHits)
            assertFalse("raw claim consumption must cancel armed chord", localHost.isAwaitingSecondStroke)
            assertFalse(localHost.onPreviewKeyEvent(ctrlD))
            assertEquals(0, reformatHits)

            // (b) Open MenuShortcutScope consuming Alt+M cancels pending Ctrl+K chord.
            assertTrue(localHost.onPreviewKeyEvent(ctrlK))
            assertTrue(localHost.isAwaitingSecondStroke)
            val menuScope = localHost.openMenuShortcutScope()
            try {
                menuScope.register(JewelKeyStroke(Key.M, alt = true)) { menuHits++ }
                val altM =
                    AwtKeyEvent(
                            Panel(),
                            AwtKeyEvent.KEY_PRESSED,
                            0L,
                            InputEvent.ALT_DOWN_MASK,
                            AwtKeyEvent.VK_M,
                            AwtKeyEvent.CHAR_UNDEFINED,
                        )
                        .toComposeKeyEvent()
                assertTrue(localHost.onPreviewKeyEvent(altM))
                assertEquals(1, menuHits)
                assertFalse("menu scope consumption must cancel armed chord", localHost.isAwaitingSecondStroke)
            } finally {
                menuScope.close()
            }
            assertFalse(localHost.onPreviewKeyEvent(ctrlD))
            assertEquals(0, reformatHits)
        }
    }
}
