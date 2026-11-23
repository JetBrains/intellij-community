// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jewel.ui.component

import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.withKeyDown
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import kotlinx.coroutines.runBlocking
import org.jetbrains.jewel.foundation.shortcut.InMemoryJewelKeymap
import org.jetbrains.jewel.foundation.shortcut.JewelAction
import org.jetbrains.jewel.foundation.shortcut.JewelActionId
import org.jetbrains.jewel.foundation.shortcut.JewelKeySequence
import org.jetbrains.jewel.foundation.shortcut.JewelKeyStroke
import org.jetbrains.jewel.foundation.shortcut.JewelShortcutHostState
import org.jetbrains.jewel.foundation.shortcut.ProvideJewelShortcutHost
import org.jetbrains.jewel.foundation.shortcut.ShortcutRepeatPolicy
import org.jetbrains.jewel.foundation.shortcut.claimShortcut
import org.jetbrains.jewel.foundation.shortcut.shortcut
import org.jetbrains.jewel.foundation.shortcut.toComposeKeyEvent
import org.jetbrains.jewel.intui.standalone.theme.IntUiTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** Verifies Jewel [Popup] automatically attaches [JewelShortcutHostState.resolverRootModifier] and preview dispatch. */
internal class PopupShortcutPropagationTest {
    @JvmField @Rule internal val rule = createComposeRule()

    private val popupAction = JewelAction(JewelActionId("test.popup.action"), "Popup Action")
    private val mainAction = JewelAction(JewelActionId("test.main.action"), "Main Action")

    private val keymap =
        InMemoryJewelKeymap("popup-test").apply {
            bind(popupAction.id, JewelKeySequence(JewelKeyStroke(Key.P, ctrl = true)))
            bind(mainAction.id, JewelKeySequence(JewelKeyStroke(Key.M, ctrl = true)))
        }

    private val positionProvider =
        object : PopupPositionProvider {
            override fun calculatePosition(
                anchorBounds: IntRect,
                windowSize: IntSize,
                layoutDirection: LayoutDirection,
                popupContentSize: IntSize,
            ): IntOffset = IntOffset.Zero
        }

    @Test
    fun `shortcuts and claims inside Popup register, dispatch, and detach without disturbing main window`() {
        val host = JewelShortcutHostState { keymap }
        val mainFocus = FocusRequester()
        val popupFocus = FocusRequester()
        var showPopup by mutableStateOf(false)
        var mainHits = 0
        var popupCommandHits = 0
        var popupClaimHits = 0

        rule.setContent {
            IntUiTheme {
                ProvideJewelShortcutHost(host) {
                    Box(host.resolverRootModifier) {
                        Box(
                            Modifier.testTag("mainSurface")
                                .shortcut(mainAction) { mainHits++ }
                                .focusRequester(mainFocus)
                                .focusable()
                        )
                        if (showPopup) {
                            Popup(
                                popupPositionProvider = positionProvider,
                                onDismissRequest = { showPopup = false },
                                properties = PopupProperties(focusable = true),
                            ) {
                                Box(
                                    Modifier.testTag("popupSurface")
                                        .shortcut(popupAction) { popupCommandHits++ }
                                        .claimShortcut(JewelKeySequence(JewelKeyStroke(Key.G, alt = true))) {
                                            popupClaimHits++
                                        }
                                        .focusRequester(popupFocus)
                                        .focusable()
                                )
                            }
                        }
                    }
                }
            }
        }

        runBlocking {
            rule.runOnIdle { mainFocus.requestFocus() }
            rule.awaitIdle()
        }
        rule.onNodeWithTag("mainSurface").performKeyInput { withKeyDown(Key.CtrlLeft) { pressKey(Key.M) } }
        rule.waitForIdle()
        assertEquals(1, mainHits)

        rule.runOnIdle { showPopup = true }
        rule.waitForIdle()
        runBlocking {
            rule.runOnIdle { popupFocus.requestFocus() }
            rule.awaitIdle()
        }

        rule.onNodeWithTag("popupSurface").performKeyInput {
            withKeyDown(Key.CtrlLeft) { pressKey(Key.P) }
            withKeyDown(Key.AltLeft) { pressKey(Key.G) }
        }
        rule.waitForIdle()
        assertEquals(1, popupCommandHits)
        assertEquals(1, popupClaimHits)

        // Closing popup detaches only the popup root; main window remains bound and dispatches cleanly.
        rule.runOnIdle { showPopup = false }
        rule.waitForIdle()
        runBlocking {
            rule.runOnIdle { mainFocus.requestFocus() }
            rule.awaitIdle()
        }
        rule.onNodeWithTag("mainSurface").performKeyInput { withKeyDown(Key.CtrlLeft) { pressKey(Key.M) } }
        rule.waitForIdle()
        assertEquals(2, mainHits)
    }

    @Test
    fun `closing a non-focusable popup keeps a pending chord armed and preserves OnceUntilRelease latch`() {
        val reformat = JewelAction(JewelActionId("test.popup.reformat"), "Reformat")
        val chordMap =
            InMemoryJewelKeymap("popup-nonfocusable-chord").apply {
                bind(
                    reformat.id,
                    JewelKeySequence(JewelKeyStroke(Key.K, ctrl = true), JewelKeyStroke(Key.D, ctrl = true)),
                )
            }
        val host = JewelShortcutHostState(chordTimeoutMillis = 0L) { chordMap }
        val mainFocus = FocusRequester()
        var showTooltipLikePopup by mutableStateOf(false)
        var chordHits = 0
        var latchHits = 0

        rule.setContent {
            IntUiTheme {
                ProvideJewelShortcutHost(host) {
                    Box(host.resolverRootModifier) {
                        Box(
                            Modifier.size(64.dp)
                                .testTag("mainSurface")
                                .shortcut(reformat) { chordHits++ }
                                .claimShortcut(
                                    JewelKeySequence(JewelKeyStroke(Key.L, ctrl = true)),
                                    repeatPolicy = ShortcutRepeatPolicy.OnceUntilRelease,
                                ) {
                                    latchHits++
                                }
                                .focusRequester(mainFocus)
                                .focusable()
                        )
                        if (showTooltipLikePopup) {
                            Popup(
                                popupPositionProvider = positionProvider,
                                properties = PopupProperties(focusable = false),
                            ) {
                                Box(Modifier.size(8.dp))
                            }
                        }
                    }
                }
            }
        }
        runBlocking {
            rule.runOnIdle { mainFocus.requestFocus() }
            rule.awaitIdle()
        }
        rule.runOnIdle { showTooltipLikePopup = true }
        rule.waitForIdle()

        rule.onNodeWithTag("mainSurface").performKeyInput { withKeyDown(Key.CtrlLeft) { pressKey(Key.K) } }
        rule.waitForIdle()
        assertTrue("the chord prefix must arm", rule.runOnIdle { host.isAwaitingSecondStroke })

        rule.runOnIdle { showTooltipLikePopup = false }
        rule.waitForIdle()
        assertTrue(
            "a tooltip-like popup that never held focus must not cancel the pending chord",
            rule.runOnIdle { host.isAwaitingSecondStroke },
        )

        rule.onNodeWithTag("mainSurface").performKeyInput { withKeyDown(Key.CtrlLeft) { pressKey(Key.D) } }
        rule.waitForIdle()
        assertEquals(1, chordHits)

        // Now verify OnceUntilRelease stays latched across opening and closing a non-focusable popup.
        val ctrlLDown =
            java.awt.event
                .KeyEvent(
                    java.awt.Panel(),
                    java.awt.event.KeyEvent.KEY_PRESSED,
                    0L,
                    java.awt.event.InputEvent.CTRL_DOWN_MASK,
                    java.awt.event.KeyEvent.VK_L,
                    java.awt.event.KeyEvent.CHAR_UNDEFINED,
                )
                .toComposeKeyEvent()
        rule.runOnIdle { assertTrue(host.onPreviewKeyEvent(ctrlLDown)) }
        assertEquals(1, latchHits)

        rule.runOnIdle { showTooltipLikePopup = true }
        rule.waitForIdle()
        rule.runOnIdle { showTooltipLikePopup = false }
        rule.waitForIdle()

        rule.runOnIdle { assertTrue(host.onPreviewKeyEvent(ctrlLDown)) }
        assertEquals("non-focusable popup close must not reset OnceUntilRelease latch", 1, latchHits)
    }

    @Test
    fun `main-surface claim does not swallow a key handled by a focused Popup`() {
        val host = JewelShortcutHostState(chordTimeoutMillis = 0L) { keymap }
        val mainFocus = FocusRequester()
        val popupFocus = FocusRequester()
        var showPopup by mutableStateOf(false)
        var mainClaimHits = 0
        var popupKeyHits = 0

        rule.setContent {
            IntUiTheme {
                ProvideJewelShortcutHost(host) {
                    Box(host.resolverRootModifier) {
                        Box(
                            Modifier.size(64.dp)
                                .testTag("mainSurface")
                                .claimShortcut(JewelKeySequence(JewelKeyStroke(Key.Enter))) { mainClaimHits++ }
                                .focusRequester(mainFocus)
                                .focusable()
                        )
                        if (showPopup) {
                            Popup(
                                popupPositionProvider = positionProvider,
                                onDismissRequest = { showPopup = false },
                                properties = PopupProperties(focusable = true),
                            ) {
                                Box(
                                    Modifier.size(32.dp)
                                        .testTag("popupSurface")
                                        .onKeyEvent { event ->
                                            if (event.key == Key.Enter && event.type == KeyEventType.KeyDown) {
                                                popupKeyHits++
                                                true
                                            } else {
                                                false
                                            }
                                        }
                                        .focusRequester(popupFocus)
                                        .focusable()
                                )
                            }
                        }
                    }
                }
            }
        }
        runBlocking {
            rule.runOnIdle { mainFocus.requestFocus() }
            rule.awaitIdle()
        }
        rule.runOnIdle { showPopup = true }
        rule.waitForIdle()
        runBlocking {
            rule.runOnIdle { popupFocus.requestFocus() }
            rule.awaitIdle()
        }

        rule.onNodeWithTag("popupSurface").performKeyInput { pressKey(Key.Enter) }
        rule.waitForIdle()

        assertEquals("the claim on the surface behind the popup must not fire", 0, mainClaimHits)
        assertEquals("the focused popup must receive its own Enter", 1, popupKeyHits)
    }

    @Test
    fun `topmost attached root shadows outer claims even if outer focus stays active`() {
        val host = JewelShortcutHostState(chordTimeoutMillis = 0L) { keymap }
        val outerFocus = FocusRequester()
        var showTopRoot by mutableStateOf(false)
        var outerClaimHits = 0
        var outerCommandHits = 0

        rule.setContent {
            IntUiTheme {
                Box(host.resolverRootModifier) {
                    Box(
                        Modifier.size(32.dp)
                            .testTag("outerSurface")
                            .shortcut(mainAction) { outerCommandHits++ }
                            .claimShortcut(JewelKeySequence(JewelKeyStroke(Key.Enter))) { outerClaimHits++ }
                            .focusRequester(outerFocus)
                            .focusable()
                    )
                }
                if (showTopRoot) {
                    Box(Modifier.size(32.dp).testTag("topRoot").then(host.resolverRootModifier))
                }
            }
        }
        runBlocking {
            rule.runOnIdle { outerFocus.requestFocus() }
            rule.awaitIdle()
        }

        rule.runOnIdle { showTopRoot = true }
        rule.waitForIdle()

        // Outer still holds Compose focus (mirroring JBPopup setRequestFocus(false)), yet topRoot shadows outer claims
        // while outer commands continue to fall through.
        rule.onNodeWithTag("outerSurface").performKeyInput { pressKey(Key.Enter) }
        rule.waitForIdle()
        assertEquals("outer claim must be shadowed while a second root is topmost", 0, outerClaimHits)

        rule.onNodeWithTag("outerSurface").performKeyInput { withKeyDown(Key.CtrlLeft) { pressKey(Key.M) } }
        rule.waitForIdle()
        assertEquals("outer command must still fall through across attached roots", 1, outerCommandHits)
    }
}
