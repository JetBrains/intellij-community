// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jewel.foundation.shortcut

import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.InternalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent as ComposeKeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isAltPressed
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.junit4.createComposeRule
import java.awt.event.InputEvent
import java.awt.event.KeyEvent as AwtKeyEvent
import javax.swing.JPanel
import kotlinx.coroutines.runBlocking
import org.jetbrains.skiko.OS
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * Pins OS-specific AltGr and level-three modifier handling across [toComposeKeyEvent],
 * [JewelKeyStroke.fromKeyDownOrNull], and [JewelShortcutHostState] (JEWEL-1365).
 */
internal class JewelKeyStrokeAltGrTest {
    @JvmField @Rule internal val rule: ComposeContentTestRule = createComposeRule()

    private val source = JPanel()
    private val ctrlAlt = InputEvent.CTRL_DOWN_MASK or InputEvent.ALT_DOWN_MASK

    private fun awtKeyDown(keyCode: Int, keyChar: Char, modifiersEx: Int): AwtKeyEvent =
        AwtKeyEvent(
            source,
            AwtKeyEvent.KEY_PRESSED,
            0L,
            modifiersEx,
            keyCode,
            keyChar,
            AwtKeyEvent.KEY_LOCATION_STANDARD,
        )

    private fun awtTyped(keyChar: Char, modifiersEx: Int = 0): AwtKeyEvent =
        AwtKeyEvent(
            source,
            AwtKeyEvent.KEY_TYPED,
            0L,
            modifiersEx,
            AwtKeyEvent.VK_UNDEFINED,
            keyChar,
            AwtKeyEvent.KEY_LOCATION_UNKNOWN,
        )

    @OptIn(InternalComposeUiApi::class)
    private fun composeKeyDown(
        key: Key,
        codePoint: Int,
        ctrl: Boolean = false,
        alt: Boolean = false,
        shift: Boolean = false,
        meta: Boolean = false,
    ): ComposeKeyEvent =
        ComposeKeyEvent(
            key = key,
            type = KeyEventType.KeyDown,
            codePoint = codePoint,
            isCtrlPressed = ctrl,
            isMetaPressed = meta,
            isAltPressed = alt,
            isShiftPressed = shift,
            nativeEvent = null,
        )

    private fun assertAwtConvertedModifiers(
        event: ComposeKeyEvent,
        ctrl: Boolean,
        alt: Boolean,
        shift: Boolean = false,
        meta: Boolean = false,
    ) {
        assertEquals("toComposeKeyEvent() must preserve ctrl", ctrl, event.isCtrlPressed)
        assertEquals("toComposeKeyEvent() must preserve alt", alt, event.isAltPressed)
        assertEquals("toComposeKeyEvent() must preserve shift", shift, event.isShiftPressed)
        assertEquals("toComposeKeyEvent() must preserve meta", meta, event.isMetaPressed)
    }

    @Test
    fun `Windows Ctrl plus Alt plus printable char without AltGraph returns null stroke`() {
        val awtQ = awtKeyDown(AwtKeyEvent.VK_Q, '@', ctrlAlt).toComposeKeyEvent()
        assertAwtConvertedModifiers(awtQ, ctrl = true, alt = true)
        assertNull(JewelKeyStroke.fromKeyDownOrNull(awtQ, OS.Windows))

        val awtE = awtKeyDown(AwtKeyEvent.VK_E, 'é', ctrlAlt).toComposeKeyEvent()
        assertAwtConvertedModifiers(awtE, ctrl = true, alt = true)
        assertNull(JewelKeyStroke.fromKeyDownOrNull(awtE, OS.Windows))

        val composeQ = composeKeyDown(Key.Q, '@'.code, ctrl = true, alt = true)
        assertNull(JewelKeyStroke.fromKeyDownOrNull(composeQ, OS.Windows))
    }

    @Test
    fun `Windows explicit AltGraph returns null stroke with or without Ctrl plus Alt`() {
        val withCtrlAlt =
            awtKeyDown(AwtKeyEvent.VK_Q, '@', ctrlAlt or InputEvent.ALT_GRAPH_DOWN_MASK).toComposeKeyEvent()
        assertAwtConvertedModifiers(withCtrlAlt, ctrl = true, alt = true)
        assertNull(JewelKeyStroke.fromKeyDownOrNull(withCtrlAlt, OS.Windows))

        val altGraphOnly = awtKeyDown(AwtKeyEvent.VK_Q, '@', InputEvent.ALT_GRAPH_DOWN_MASK).toComposeKeyEvent()
        assertAwtConvertedModifiers(altGraphOnly, ctrl = false, alt = false)
        assertNull(JewelKeyStroke.fromKeyDownOrNull(altGraphOnly, OS.Windows))

        val altGraphUndefinedChar =
            awtKeyDown(AwtKeyEvent.VK_G, AwtKeyEvent.CHAR_UNDEFINED, InputEvent.ALT_GRAPH_DOWN_MASK).toComposeKeyEvent()
        assertAwtConvertedModifiers(altGraphUndefinedChar, ctrl = false, alt = false)
        assertNull(JewelKeyStroke.fromKeyDownOrNull(altGraphUndefinedChar, OS.Windows))
    }

    @Test
    fun `Windows Ctrl plus Alt with undefined, zero, or control char stays Ctrl plus Alt stroke`() {
        val expected = JewelKeyStroke(Key.Q, ctrl = true, alt = true)

        for (char in listOf(AwtKeyEvent.CHAR_UNDEFINED, '\u0000', '\u0011', '\u001b', '\u007f')) {
            val awtEvent = awtKeyDown(AwtKeyEvent.VK_Q, char, ctrlAlt).toComposeKeyEvent()
            assertAwtConvertedModifiers(awtEvent, ctrl = true, alt = true)
            assertEquals("char code ${char.code}", expected, JewelKeyStroke.fromKeyDownOrNull(awtEvent, OS.Windows))

            val composeEvent = composeKeyDown(Key.Q, char.code, ctrl = true, alt = true)
            assertEquals("char code ${char.code}", expected, JewelKeyStroke.fromKeyDownOrNull(composeEvent, OS.Windows))
        }
    }

    @Test
    fun `Linux honors explicit AltGraph without Ctrl plus Alt`() {
        val altGraphQ = awtKeyDown(AwtKeyEvent.VK_Q, '@', InputEvent.ALT_GRAPH_DOWN_MASK).toComposeKeyEvent()
        assertAwtConvertedModifiers(altGraphQ, ctrl = false, alt = false)
        assertNull(JewelKeyStroke.fromKeyDownOrNull(altGraphQ, OS.Linux))

        val altGraphDeadKey =
            awtKeyDown(AwtKeyEvent.VK_DEAD_ACUTE, AwtKeyEvent.CHAR_UNDEFINED, InputEvent.ALT_GRAPH_DOWN_MASK)
                .toComposeKeyEvent()
        assertAwtConvertedModifiers(altGraphDeadKey, ctrl = false, alt = false)
        assertNull(JewelKeyStroke.fromKeyDownOrNull(altGraphDeadKey, OS.Linux))
    }

    @Test
    fun `Linux Ctrl plus Alt plus printable char without AltGraph stays Ctrl plus Alt stroke`() {
        val expected = JewelKeyStroke(Key.Q, ctrl = true, alt = true)

        val awtQ = awtKeyDown(AwtKeyEvent.VK_Q, '@', ctrlAlt).toComposeKeyEvent()
        assertAwtConvertedModifiers(awtQ, ctrl = true, alt = true)
        assertEquals(expected, JewelKeyStroke.fromKeyDownOrNull(awtQ, OS.Linux))

        val composeQ = composeKeyDown(Key.Q, '@'.code, ctrl = true, alt = true)
        assertEquals(expected, JewelKeyStroke.fromKeyDownOrNull(composeQ, OS.Linux))

        // Compose-only event with no native AWT payload and no level-three signal remains a normal stroke.
        val bareComposeQ = composeKeyDown(Key.Q, '@'.code)
        assertEquals(JewelKeyStroke(Key.Q), JewelKeyStroke.fromKeyDownOrNull(bareComposeQ, OS.Linux))
    }

    @Test
    fun `macOS Ctrl plus Option with printable char preserves Ctrl plus Alt stroke`() {
        val cases =
            listOf(
                Triple(AwtKeyEvent.VK_1, Key.One, '1'),
                Triple(AwtKeyEvent.VK_COMMA, Key.Comma, ','),
                Triple(AwtKeyEvent.VK_SLASH, Key.Slash, '/'),
            )

        for ((vk, key, char) in cases) {
            val expected = JewelKeyStroke(key, ctrl = true, alt = true)
            val awtEvent = awtKeyDown(vk, char, ctrlAlt).toComposeKeyEvent()
            assertAwtConvertedModifiers(awtEvent, ctrl = true, alt = true)
            assertEquals("macOS $char", expected, JewelKeyStroke.fromKeyDownOrNull(awtEvent, OS.MacOS))

            val composeEvent = composeKeyDown(key, char.code, ctrl = true, alt = true)
            assertEquals("macOS $char", expected, JewelKeyStroke.fromKeyDownOrNull(composeEvent, OS.MacOS))
        }
    }

    @Test
    fun `macOS Option or Ctrl plus Option with spurious AltGraph bit preserves original stroke`() {
        val optionOnly =
            awtKeyDown(AwtKeyEvent.VK_Q, 'œ', InputEvent.ALT_DOWN_MASK or InputEvent.ALT_GRAPH_DOWN_MASK)
                .toComposeKeyEvent()
        assertAwtConvertedModifiers(optionOnly, ctrl = false, alt = true)
        assertEquals(JewelKeyStroke(Key.Q, alt = true), JewelKeyStroke.fromKeyDownOrNull(optionOnly, OS.MacOS))

        val ctrlOption =
            awtKeyDown(AwtKeyEvent.VK_Q, '@', ctrlAlt or InputEvent.ALT_GRAPH_DOWN_MASK).toComposeKeyEvent()
        assertAwtConvertedModifiers(ctrlOption, ctrl = true, alt = true)
        assertEquals(
            JewelKeyStroke(Key.Q, ctrl = true, alt = true),
            JewelKeyStroke.fromKeyDownOrNull(ctrlOption, OS.MacOS),
        )
    }

    @Test
    fun `bare Q without AltGraph resolves to bare Q across all OSes`() {
        val expected = JewelKeyStroke(Key.Q)
        val awtBareQ = awtKeyDown(AwtKeyEvent.VK_Q, 'q', modifiersEx = 0).toComposeKeyEvent()
        assertAwtConvertedModifiers(awtBareQ, ctrl = false, alt = false)
        val composeBareQ = composeKeyDown(Key.Q, 'q'.code)

        for (os in listOf(OS.Windows, OS.Linux, OS.MacOS)) {
            assertEquals("AWT bare Q on $os", expected, JewelKeyStroke.fromKeyDownOrNull(awtBareQ, os))
            assertEquals("Compose bare Q on $os", expected, JewelKeyStroke.fromKeyDownOrNull(composeBareQ, os))
        }
    }

    @Test
    fun `Windows AltGr press and typed do not match bare-key claim, menu scope, or keymap binding`() {
        val actionQ = JewelAction(JewelActionId("test.altgr.bareQ"), "Bare Q")
        val keymap =
            InMemoryJewelKeymap("altgr-test").apply { bind(actionQ.id, JewelKeySequence(JewelKeyStroke(Key.Q))) }
        val host = JewelShortcutHostState { keymap }
        val focus = FocusRequester()
        var claimHits = 0
        var keymapHits = 0
        var menuHits = 0

        rule.setContent {
            Box(host.resolverRootModifier) {
                Box(
                    Modifier.shortcut(actionQ) { keymapHits++ }
                        .claimShortcut(JewelKeySequence(JewelKeyStroke(Key.Q))) { claimHits++ }
                        .focusRequester(focus)
                        .focusable()
                )
            }
        }
        runBlocking {
            rule.runOnIdle { focus.requestFocus() }
            rule.awaitIdle()
        }

        rule.runOnIdle {
            val menuScope = host.openMenuShortcutScope()
            menuScope.register(JewelKeyStroke(Key.Q)) { menuHits++ }
            try {
                val altGrQPress = awtKeyDown(AwtKeyEvent.VK_Q, '@', ctrlAlt).toComposeKeyEvent()
                val altGrQTyped = awtTyped('@', ctrlAlt or InputEvent.ALT_GRAPH_DOWN_MASK).toComposeKeyEvent()

                assertFalse("AltGr+Q must not veto IDE keymap", host.claimsKeyDown(altGrQPress, OS.Windows))
                assertFalse("AltGr+Q press must pass through host", host.onPreviewKeyEvent(altGrQPress, OS.Windows))
                assertFalse("AltGr+Q typed '@' must pass through host", host.onPreviewKeyEvent(altGrQTyped, OS.Windows))
                assertEquals(0, claimHits)
                assertEquals(0, keymapHits)
                assertEquals(0, menuHits)
            } finally {
                menuScope.close()
            }
        }
    }

    @Test
    fun `Windows AltGr press clears stale typed suppression without cancelling a pending chord`() {
        val chordAction = JewelAction(JewelActionId("test.altgr.chord"), "Chord")
        val keymap =
            InMemoryJewelKeymap("altgr-chord").apply {
                bind(
                    chordAction.id,
                    JewelKeySequence(JewelKeyStroke(Key.K, ctrl = true), JewelKeyStroke(Key.D, ctrl = true)),
                )
            }
        val host = JewelShortcutHostState { keymap }
        val focus = FocusRequester()
        var chordHits = 0
        var bareQClaimHits = 0

        rule.setContent {
            Box(host.resolverRootModifier) {
                Box(
                    Modifier.shortcut(chordAction) { chordHits++ }
                        .claimShortcut(JewelKeySequence(JewelKeyStroke(Key.Q))) { bareQClaimHits++ }
                        .focusRequester(focus)
                        .focusable()
                )
            }
        }
        runBlocking {
            rule.runOnIdle { focus.requestFocus() }
            rule.awaitIdle()
        }

        rule.runOnIdle {
            // 1. Arm both a pending chord and suppressNextTyped (no KEY_TYPED delivered for Ctrl+K).
            val ctrlKPress =
                awtKeyDown(AwtKeyEvent.VK_K, AwtKeyEvent.CHAR_UNDEFINED, InputEvent.CTRL_DOWN_MASK).toComposeKeyEvent()
            assertTrue(host.onPreviewKeyEvent(ctrlKPress, OS.Windows))
            assertTrue(host.isAwaitingSecondStroke)

            // 2. Windows AltGr+Q arrives: must pass through, clear stale suppression for '@', and keep the chord alive.
            val altGrQPress = awtKeyDown(AwtKeyEvent.VK_Q, '@', ctrlAlt).toComposeKeyEvent()
            val altGrQTyped = awtTyped('@', ctrlAlt or InputEvent.ALT_GRAPH_DOWN_MASK).toComposeKeyEvent()
            assertFalse(host.claimsKeyDown(altGrQPress, OS.Windows))
            assertFalse(host.onPreviewKeyEvent(altGrQPress, OS.Windows))
            assertFalse("Stale typed suppression must not swallow '@'", host.onPreviewKeyEvent(altGrQTyped, OS.Windows))
            assertTrue("AltGr press must not cancel an in-flight chord", host.isAwaitingSecondStroke)
            assertEquals(0, bareQClaimHits)
            assertEquals(0, chordHits)

            // 3. Second stroke completes the pending chord.
            val ctrlDPress =
                awtKeyDown(AwtKeyEvent.VK_D, AwtKeyEvent.CHAR_UNDEFINED, InputEvent.CTRL_DOWN_MASK).toComposeKeyEvent()
            assertTrue(host.onPreviewKeyEvent(ctrlDPress, OS.Windows))
            assertEquals(1, chordHits)
            assertFalse(host.isAwaitingSecondStroke)
        }
    }

    @Test
    fun `Windows AltGr event still honors an explicit matching claimKeyEvent handler`() {
        val chordAction = JewelAction(JewelActionId("test.altgr.rawChord"), "Chord")
        val keymap =
            InMemoryJewelKeymap("altgr-raw").apply {
                bind(
                    chordAction.id,
                    JewelKeySequence(JewelKeyStroke(Key.K, ctrl = true), JewelKeyStroke(Key.D, ctrl = true)),
                )
            }
        val host = JewelShortcutHostState { keymap }
        val focus = FocusRequester()
        var rawHits = 0

        rule.setContent {
            Box(host.resolverRootModifier) {
                Box(
                    Modifier.shortcut(chordAction) {}
                        .claimKeyEvent(matcher = { it.key == Key.Q && it.isCtrlPressed && it.isAltPressed }) {
                            rawHits++
                        }
                        .focusRequester(focus)
                        .focusable()
                )
            }
        }
        runBlocking {
            rule.runOnIdle { focus.requestFocus() }
            rule.awaitIdle()
        }

        rule.runOnIdle {
            val ctrlKPress =
                awtKeyDown(AwtKeyEvent.VK_K, AwtKeyEvent.CHAR_UNDEFINED, InputEvent.CTRL_DOWN_MASK).toComposeKeyEvent()
            assertTrue(host.onPreviewKeyEvent(ctrlKPress, OS.Windows))
            assertTrue(host.isAwaitingSecondStroke)

            val altGrQPress = awtKeyDown(AwtKeyEvent.VK_Q, '@', ctrlAlt).toComposeKeyEvent()
            val altGrQTyped = awtTyped('@', ctrlAlt or InputEvent.ALT_GRAPH_DOWN_MASK).toComposeKeyEvent()

            assertTrue("Explicit raw claim must veto", host.claimsKeyDown(altGrQPress, OS.Windows))
            assertTrue("Explicit raw claim must consume press", host.onPreviewKeyEvent(altGrQPress, OS.Windows))
            assertEquals(1, rawHits)
            assertFalse("Consumed raw claim cancels pending chord", host.isAwaitingSecondStroke)
            assertTrue(
                "Consumed raw claim must suppress trailing KEY_TYPED",
                host.onPreviewKeyEvent(altGrQTyped, OS.Windows),
            )
        }
    }
}
