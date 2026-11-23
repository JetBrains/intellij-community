// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jewel.foundation.shortcut

import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.platform.WindowInfo
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.junit4.createComposeRule
import java.awt.Panel
import java.awt.event.InputEvent
import java.awt.event.KeyEvent as AwtKeyEvent
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** UI tests for multi-window command boundaries when a modal window takes focus from an owner window. */
internal class ShortcutHostWindowFocusUiTest {
    @JvmField @Rule internal val rule: ComposeContentTestRule = createComposeRule()

    private val ownerFocus = FocusRequester()
    private val modalFocus = FocusRequester()

    private fun focus(requester: FocusRequester) {
        runBlocking {
            rule.runOnIdle { requester.requestFocus() }
            rule.awaitIdle()
        }
    }

    @Test
    fun `focused modal window root blocks command bindings in a deactivated owner window`() {
        val ownerWindow =
            object : WindowInfo {
                override var isWindowFocused: Boolean by mutableStateOf(true)
            }
        val modalWindow =
            object : WindowInfo {
                override var isWindowFocused: Boolean by mutableStateOf(false)
            }
        val cut = JewelAction(JewelActionId("test.ui.modalCut"), "Cut")
        val keymap =
            InMemoryJewelKeymap("modal").apply { bind(cut.id, JewelKeySequence(JewelKeyStroke(Key.X, ctrl = true))) }
        val localHost = JewelShortcutHostState { keymap }
        var ownerCuts = 0
        var showModal by mutableStateOf(false)

        rule.setContent {
            CompositionLocalProvider(LocalWindowInfo provides ownerWindow) {
                Box(localHost.resolverRootModifier) {
                    Box(Modifier.shortcut(cut) { ownerCuts++ }.focusRequester(ownerFocus).focusable())
                }
            }
            if (showModal) {
                CompositionLocalProvider(LocalWindowInfo provides modalWindow) {
                    Box(localHost.resolverRootModifier) { Box(Modifier.focusRequester(modalFocus).focusable()) }
                }
            }
        }
        focus(ownerFocus)

        val ctrlX =
            AwtKeyEvent(
                    Panel(),
                    AwtKeyEvent.KEY_PRESSED,
                    0L,
                    InputEvent.CTRL_DOWN_MASK,
                    AwtKeyEvent.VK_X,
                    AwtKeyEvent.CHAR_UNDEFINED,
                )
                .toComposeKeyEvent()

        rule.runOnIdle {
            assertTrue(localHost.onPreviewKeyEvent(ctrlX))
            assertEquals(1, ownerCuts)
            ownerWindow.isWindowFocused = false
            modalWindow.isWindowFocused = true
            showModal = true
        }
        rule.waitForIdle()

        rule.runOnIdle {
            assertFalse(
                "deactivated owner command binding must not fire while a modal window root is focused",
                localHost.onPreviewKeyEvent(ctrlX),
            )
            assertEquals(1, ownerCuts)
        }
    }
}
