// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jewel.bridge

import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.junit4.createComposeRule
import java.awt.Component
import java.awt.DefaultKeyboardFocusManager
import java.awt.KeyboardFocusManager
import java.awt.event.InputEvent
import java.awt.event.KeyEvent as AwtKeyEvent
import javax.swing.JPanel
import javax.swing.KeyStroke
import javax.swing.SwingUtilities
import kotlinx.coroutines.runBlocking
import org.jetbrains.jewel.foundation.shortcut.InMemoryJewelKeymap
import org.jetbrains.jewel.foundation.shortcut.JewelAction
import org.jetbrains.jewel.foundation.shortcut.JewelActionId
import org.jetbrains.jewel.foundation.shortcut.JewelKeySequence
import org.jetbrains.jewel.foundation.shortcut.JewelKeyStroke
import org.jetbrains.jewel.foundation.shortcut.JewelShortcutHostState
import org.jetbrains.jewel.foundation.shortcut.ShortcutRepeatPolicy
import org.jetbrains.jewel.foundation.shortcut.claimKeyEvent
import org.jetbrains.jewel.foundation.shortcut.claimShortcut
import org.jetbrains.jewel.foundation.shortcut.shortcut
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * Headless coverage of the IJPL bridge claim lane: [JewelComposePanelWrapper.skipKeyEventDispatcher]
 * (`KeyboardAwareContainer`), `shortcutClaimEvaluator`, open menu scope vetoes, and [installBridgeClaimLane]'s
 * `KeyEventDispatcher` delivering `KEY_PRESSED`, suppressing `KEY_TYPED`, and releasing `OnceUntilRelease` latches on
 * `KEY_RELEASED`.
 */
internal class JewelBridgeClaimLaneTest {
    @JvmField @Rule internal val rule: ComposeContentTestRule = createComposeRule()

    private class StubFocusManager(private val owner: Component) : DefaultKeyboardFocusManager() {
        override fun getFocusOwner(): Component = owner
    }

    private class MovableFocusManager(var owner: Component) : DefaultKeyboardFocusManager() {
        override fun getFocusOwner(): Component = owner
    }

    @Test
    fun `KeyboardAwareContainer vetoes focused claims and open menu scopes while passing commands and unbound keys`() {
        val host = JewelShortcutHostState { InMemoryJewelKeymap("bridge-empty") }
        lateinit var wrapper: JewelComposePanelWrapper
        SwingUtilities.invokeAndWait { wrapper = JewelComposePanelWrapper(focusOnClickInside = false) }
        val focus = FocusRequester()
        val commandAction = JewelAction(JewelActionId("bridge.claim.command"), "Command")
        var claimHits = 0
        var rawHits = 0
        var menuHits = 0

        rule.setContent {
            Box(host.resolverRootModifier) {
                Box(
                    Modifier.shortcut(commandAction) {}
                        .claimShortcut(
                            JewelKeySequence(JewelKeyStroke(Key.K, ctrl = true)),
                            repeatPolicy = ShortcutRepeatPolicy.OnceUntilRelease,
                        ) {
                            claimHits++
                        }
                        .claimKeyEvent(matcher = { it.key == Key.J && it.isCtrlPressed }) { rawHits++ }
                        .focusRequester(focus)
                        .focusable()
                )
            }
        }
        runBlocking {
            rule.runOnIdle { focus.requestFocus() }
            rule.awaitIdle()
        }

        SwingUtilities.invokeAndWait {
            val childFocusOwner = JPanel().also { wrapper.composePanel.add(it) }
            val prevManager = KeyboardFocusManager.getCurrentKeyboardFocusManager()
            KeyboardFocusManager.setCurrentKeyboardFocusManager(StubFocusManager(childFocusOwner))
            val removeLane = installBridgeClaimLane(wrapper, host)
            try {
                val ctrlKDown =
                    AwtKeyEvent(
                        childFocusOwner,
                        AwtKeyEvent.KEY_PRESSED,
                        0L,
                        InputEvent.CTRL_DOWN_MASK,
                        AwtKeyEvent.VK_K,
                        'k',
                    )
                val ctrlJDown =
                    AwtKeyEvent(
                        childFocusOwner,
                        AwtKeyEvent.KEY_PRESSED,
                        0L,
                        InputEvent.CTRL_DOWN_MASK,
                        AwtKeyEvent.VK_J,
                        'j',
                    )
                val unboundDown = AwtKeyEvent(childFocusOwner, AwtKeyEvent.KEY_PRESSED, 0L, 0, AwtKeyEvent.VK_X, 'x')

                assertTrue(
                    "claimShortcut must veto IDE keymap via KeyboardAwareContainer",
                    wrapper.skipKeyEventDispatcher(childFocusOwner, ctrlKDown),
                )
                assertTrue(
                    "claimKeyEvent must veto IDE keymap via KeyboardAwareContainer",
                    wrapper.skipKeyEventDispatcher(childFocusOwner, ctrlJDown),
                )
                assertFalse(
                    "unbound strokes must pass IDE keymap veto",
                    wrapper.skipKeyEventDispatcher(childFocusOwner, unboundDown),
                )

                // Open menu scope must veto IDE keymap, deliver via KeyEventDispatcher, and suppress KEY_TYPED.
                val menuScope = host.openMenuShortcutScope()
                menuScope.register(JewelKeyStroke(Key.M, alt = true)) { menuHits++ }
                val altMDown =
                    AwtKeyEvent(
                        childFocusOwner,
                        AwtKeyEvent.KEY_PRESSED,
                        0L,
                        InputEvent.ALT_DOWN_MASK,
                        AwtKeyEvent.VK_M,
                        AwtKeyEvent.CHAR_UNDEFINED,
                    )
                assertTrue(
                    "open menu scope must veto IDE keymap",
                    wrapper.skipKeyEventDispatcher(childFocusOwner, altMDown),
                )

                val fm = KeyboardFocusManager.getCurrentKeyboardFocusManager()
                fm.dispatchEvent(altMDown)
                assertTrue("open menu scope must consume matched KEY_PRESSED", altMDown.isConsumed)
                assertEquals("open menu scope must invoke handler", 1, menuHits)

                val mTyped = AwtKeyEvent(childFocusOwner, AwtKeyEvent.KEY_TYPED, 0L, 0, AwtKeyEvent.VK_UNDEFINED, 'm')
                fm.dispatchEvent(mTyped)
                assertTrue("trailing KEY_TYPED after menu scope dispatch must be consumed", mTyped.isConsumed)

                menuScope.close()
                val altMClosed =
                    AwtKeyEvent(
                        childFocusOwner,
                        AwtKeyEvent.KEY_PRESSED,
                        0L,
                        InputEvent.ALT_DOWN_MASK,
                        AwtKeyEvent.VK_M,
                        AwtKeyEvent.CHAR_UNDEFINED,
                    )
                assertFalse(
                    "closed menu scope must stop vetoing",
                    wrapper.skipKeyEventDispatcher(childFocusOwner, altMClosed),
                )
                fm.dispatchEvent(altMClosed)
                assertFalse("closed menu scope must pass key through", altMClosed.isConsumed)
                assertEquals(1, menuHits)

                // Deliver via KeyEventDispatcher stage: KEY_PRESSED consumes + invokes, KEY_TYPED suppresses,
                // repeat press latches until KEY_RELEASED clears OnceUntilRelease.
                fm.dispatchEvent(ctrlKDown)
                assertTrue(ctrlKDown.isConsumed)
                assertEquals(1, claimHits)

                val kTyped = AwtKeyEvent(childFocusOwner, AwtKeyEvent.KEY_TYPED, 0L, 0, AwtKeyEvent.VK_UNDEFINED, 'k')
                fm.dispatchEvent(kTyped)
                assertTrue("trailing KEY_TYPED must be consumed", kTyped.isConsumed)

                val ctrlKRepeat =
                    AwtKeyEvent(
                        childFocusOwner,
                        AwtKeyEvent.KEY_PRESSED,
                        0L,
                        InputEvent.CTRL_DOWN_MASK,
                        AwtKeyEvent.VK_K,
                        'k',
                    )
                fm.dispatchEvent(ctrlKRepeat)
                assertTrue(ctrlKRepeat.isConsumed)
                assertEquals("held repeat must be suppressed under OnceUntilRelease", 1, claimHits)

                // Release modifier first (Ctrl up): must NOT consume KEY_RELEASED and must re-arm OnceUntilRelease.
                val ctrlUp =
                    AwtKeyEvent(
                        childFocusOwner,
                        AwtKeyEvent.KEY_RELEASED,
                        0L,
                        0,
                        AwtKeyEvent.VK_CONTROL,
                        AwtKeyEvent.CHAR_UNDEFINED,
                    )
                fm.dispatchEvent(ctrlUp)
                assertFalse("KEY_RELEASED must not be consumed", ctrlUp.isConsumed)

                val ctrlKAgain =
                    AwtKeyEvent(
                        childFocusOwner,
                        AwtKeyEvent.KEY_PRESSED,
                        0L,
                        InputEvent.CTRL_DOWN_MASK,
                        AwtKeyEvent.VK_K,
                        'k',
                    )
                fm.dispatchEvent(ctrlKAgain)
                assertTrue(ctrlKAgain.isConsumed)
                assertEquals("OnceUntilRelease must re-arm after KEY_RELEASED", 2, claimHits)

                fm.dispatchEvent(ctrlJDown)
                assertTrue(ctrlJDown.isConsumed)
                assertEquals(1, rawHits)
                assertEquals(1, menuHits)
            } finally {
                removeLane()
                KeyboardFocusManager.setCurrentKeyboardFocusManager(prevManager)
            }
        }
    }

    @Test
    fun `unclaimed non-modifier key-down held before delivery retires stale typed suppression`() {
        val host = JewelShortcutHostState { InMemoryJewelKeymap("bridge-empty") }
        lateinit var wrapper: JewelComposePanelWrapper
        SwingUtilities.invokeAndWait { wrapper = JewelComposePanelWrapper(focusOnClickInside = false) }
        val focus = FocusRequester()
        var ctrlKHits = 0
        var bareQHits = 0

        rule.setContent {
            Box(host.resolverRootModifier) {
                Box(
                    Modifier.claimShortcut(JewelKeySequence(JewelKeyStroke(Key.K, ctrl = true))) { ctrlKHits++ }
                        .claimShortcut(JewelKeySequence(JewelKeyStroke(Key.Q))) { bareQHits++ }
                        .focusRequester(focus)
                        .focusable()
                )
            }
        }
        runBlocking {
            rule.runOnIdle { focus.requestFocus() }
            rule.awaitIdle()
        }

        SwingUtilities.invokeAndWait {
            val childFocusOwner = JPanel().also { wrapper.composePanel.add(it) }
            val prevManager = KeyboardFocusManager.getCurrentKeyboardFocusManager()
            KeyboardFocusManager.setCurrentKeyboardFocusManager(StubFocusManager(childFocusOwner))
            val removeLane = installBridgeClaimLane(wrapper, host)
            val fm = KeyboardFocusManager.getCurrentKeyboardFocusManager()
            try {
                // Phase A: a modifier-only key-down inspected at the veto stage must NOT clear active typed
                // suppression.
                val ctrlKDown1 =
                    AwtKeyEvent(
                        childFocusOwner,
                        AwtKeyEvent.KEY_PRESSED,
                        0L,
                        InputEvent.CTRL_DOWN_MASK,
                        AwtKeyEvent.VK_K,
                        AwtKeyEvent.CHAR_UNDEFINED,
                    )
                fm.dispatchEvent(ctrlKDown1)
                assertTrue(ctrlKDown1.isConsumed)
                assertEquals(1, ctrlKHits)

                val shiftDown =
                    AwtKeyEvent(
                        childFocusOwner,
                        AwtKeyEvent.KEY_PRESSED,
                        0L,
                        InputEvent.SHIFT_DOWN_MASK,
                        AwtKeyEvent.VK_SHIFT,
                        AwtKeyEvent.CHAR_UNDEFINED,
                    )
                assertFalse(wrapper.skipKeyEventDispatcher(childFocusOwner, shiftDown))

                val kTyped = AwtKeyEvent(childFocusOwner, AwtKeyEvent.KEY_TYPED, 0L, 0, AwtKeyEvent.VK_UNDEFINED, 'k')
                fm.dispatchEvent(kTyped)
                assertTrue("modifier-only veto check must not clear active typed suppression", kTyped.isConsumed)

                // Phase B: an unclaimed non-modifier key-down inspected by skipKeyEventDispatcher and then held by
                // IdeKeyEventDispatcher (never dispatched to claimDeliveryDispatcher) must retire stale suppression.
                val ctrlKDown2 =
                    AwtKeyEvent(
                        childFocusOwner,
                        AwtKeyEvent.KEY_PRESSED,
                        0L,
                        InputEvent.CTRL_DOWN_MASK,
                        AwtKeyEvent.VK_K,
                        AwtKeyEvent.CHAR_UNDEFINED,
                    )
                fm.dispatchEvent(ctrlKDown2)
                assertTrue(ctrlKDown2.isConsumed)
                assertEquals(2, ctrlKHits)

                val heldAltGrQDown =
                    AwtKeyEvent(
                        childFocusOwner,
                        AwtKeyEvent.KEY_PRESSED,
                        0L,
                        InputEvent.CTRL_DOWN_MASK or InputEvent.ALT_DOWN_MASK,
                        AwtKeyEvent.VK_Q,
                        '@',
                    )
                assertFalse(
                    "AltGr+Q must pass veto without matching bare Q",
                    wrapper.skipKeyEventDispatcher(childFocusOwner, heldAltGrQDown),
                )

                val atTyped =
                    AwtKeyEvent(
                        childFocusOwner,
                        AwtKeyEvent.KEY_TYPED,
                        0L,
                        InputEvent.ALT_GRAPH_DOWN_MASK,
                        AwtKeyEvent.VK_UNDEFINED,
                        '@',
                    )
                fm.dispatchEvent(atTyped)
                assertFalse(
                    "trailing KEY_TYPED('@') must pass through when IdeKeyEventDispatcher held the press",
                    atTyped.isConsumed,
                )
                assertEquals(0, bareQHits)
            } finally {
                removeLane()
                KeyboardFocusManager.setCurrentKeyboardFocusManager(prevManager)
            }
        }
    }

    @Test
    fun `a Ctrl plus Enter claim also vetoes Ctrl plus numpad Enter`() {
        val host = JewelShortcutHostState { InMemoryJewelKeymap("bridge-empty") }
        lateinit var wrapper: JewelComposePanelWrapper
        SwingUtilities.invokeAndWait { wrapper = JewelComposePanelWrapper(focusOnClickInside = false) }
        val focus = FocusRequester()

        rule.setContent {
            Box(host.resolverRootModifier) {
                Box(
                    Modifier.claimShortcut(JewelKeySequence(JewelKeyStroke(Key.Enter, ctrl = true))) {}
                        .focusRequester(focus)
                        .focusable()
                )
            }
        }
        runBlocking {
            rule.runOnIdle { focus.requestFocus() }
            rule.awaitIdle()
        }

        SwingUtilities.invokeAndWait {
            val childFocusOwner = JPanel().also { wrapper.composePanel.add(it) }
            val prevManager = KeyboardFocusManager.getCurrentKeyboardFocusManager()
            KeyboardFocusManager.setCurrentKeyboardFocusManager(StubFocusManager(childFocusOwner))
            val removeLane = installBridgeClaimLane(wrapper, host)

            fun ctrlEnter(location: Int) =
                AwtKeyEvent(
                    childFocusOwner,
                    AwtKeyEvent.KEY_PRESSED,
                    0L,
                    InputEvent.CTRL_DOWN_MASK,
                    AwtKeyEvent.VK_ENTER,
                    '\n',
                    location,
                )
            try {
                val mainEnter = ctrlEnter(AwtKeyEvent.KEY_LOCATION_STANDARD)
                val numpadEnter = ctrlEnter(AwtKeyEvent.KEY_LOCATION_NUMPAD)

                assertEquals(KeyStroke.getKeyStrokeForEvent(mainEnter), KeyStroke.getKeyStrokeForEvent(numpadEnter))
                assertTrue("claim must veto Ctrl+Enter", wrapper.skipKeyEventDispatcher(childFocusOwner, mainEnter))
                assertTrue(
                    "claim must also veto Ctrl+numpad Enter",
                    wrapper.skipKeyEventDispatcher(childFocusOwner, numpadEnter),
                )
            } finally {
                removeLane()
                KeyboardFocusManager.setCurrentKeyboardFocusManager(prevManager)
            }
        }
    }

    @Test
    fun `a OnceUntilRelease claim fires again after its key-up went to another window`() {
        val host = JewelShortcutHostState { InMemoryJewelKeymap("bridge-empty") }
        lateinit var wrapper: JewelComposePanelWrapper
        SwingUtilities.invokeAndWait { wrapper = JewelComposePanelWrapper(focusOnClickInside = false) }
        val focus = FocusRequester()
        var claimHits = 0

        rule.setContent {
            Box(host.resolverRootModifier) {
                Box(
                    Modifier.claimShortcut(
                            JewelKeySequence(JewelKeyStroke(Key.K, ctrl = true)),
                            repeatPolicy = ShortcutRepeatPolicy.OnceUntilRelease,
                        ) {
                            claimHits++
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

        SwingUtilities.invokeAndWait {
            val panelFocusOwner = JPanel().also { wrapper.composePanel.add(it) }
            val dialogFocusOwner = JPanel()
            val focusManager = MovableFocusManager(panelFocusOwner)
            val prevManager = KeyboardFocusManager.getCurrentKeyboardFocusManager()
            KeyboardFocusManager.setCurrentKeyboardFocusManager(focusManager)
            val removeLane = installBridgeClaimLane(wrapper, host)

            fun ctrlK(source: Component, id: Int) =
                AwtKeyEvent(source, id, 0L, InputEvent.CTRL_DOWN_MASK, AwtKeyEvent.VK_K, 'k')
            try {
                focusManager.dispatchEvent(ctrlK(panelFocusOwner, AwtKeyEvent.KEY_PRESSED))
                assertEquals(1, claimHits)

                // Handler opened a dialog: key-up goes to the dialog outside the panel wrapper.
                focusManager.owner = dialogFocusOwner
                val dialogRelease = ctrlK(dialogFocusOwner, AwtKeyEvent.KEY_RELEASED)
                focusManager.dispatchEvent(dialogRelease)
                assertFalse("external KEY_RELEASED must never be consumed", dialogRelease.isConsumed)

                // Dialog closes and AWT focus returns to the panel without Compose releasing focus.
                focusManager.owner = panelFocusOwner
                focusManager.dispatchEvent(ctrlK(panelFocusOwner, AwtKeyEvent.KEY_PRESSED))
                assertEquals("first press after dialog closed must not be swallowed", 2, claimHits)
            } finally {
                removeLane()
                KeyboardFocusManager.setCurrentKeyboardFocusManager(prevManager)
            }
        }
    }

    @Test
    fun `claimDeliveryDispatcher skips already-consumed KEY_PRESSED and KEY_TYPED while clearing latches on consumed KEY_RELEASED`() {
        val host = JewelShortcutHostState { InMemoryJewelKeymap("bridge-empty") }
        lateinit var wrapper: JewelComposePanelWrapper
        SwingUtilities.invokeAndWait { wrapper = JewelComposePanelWrapper(focusOnClickInside = false) }
        val focus = FocusRequester()
        var claimHits = 0

        rule.setContent {
            Box(host.resolverRootModifier) {
                Box(
                    Modifier.claimShortcut(
                            JewelKeySequence(JewelKeyStroke(Key.C, ctrl = true)),
                            repeatPolicy = ShortcutRepeatPolicy.OnceUntilRelease,
                        ) {
                            claimHits++
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

        SwingUtilities.invokeAndWait {
            val childFocusOwner = JPanel().also { wrapper.composePanel.add(it) }
            val prevManager = KeyboardFocusManager.getCurrentKeyboardFocusManager()
            KeyboardFocusManager.setCurrentKeyboardFocusManager(StubFocusManager(childFocusOwner))
            val removeLane = installBridgeClaimLane(wrapper, host)
            val fm = KeyboardFocusManager.getCurrentKeyboardFocusManager()
            try {
                val alreadyConsumedPress =
                    AwtKeyEvent(
                            childFocusOwner,
                            AwtKeyEvent.KEY_PRESSED,
                            0L,
                            InputEvent.CTRL_DOWN_MASK,
                            AwtKeyEvent.VK_C,
                            AwtKeyEvent.CHAR_UNDEFINED,
                        )
                        .apply { consume() }
                fm.dispatchEvent(alreadyConsumedPress)
                assertEquals("consumed KEY_PRESSED must not fire Jewel claim", 0, claimHits)

                val freshPress =
                    AwtKeyEvent(
                        childFocusOwner,
                        AwtKeyEvent.KEY_PRESSED,
                        0L,
                        InputEvent.CTRL_DOWN_MASK,
                        AwtKeyEvent.VK_C,
                        AwtKeyEvent.CHAR_UNDEFINED,
                    )
                fm.dispatchEvent(freshPress)
                assertEquals(1, claimHits)

                // An already-consumed KEY_TYPED must be skipped without clearing the host's typed-suppression latch,
                // so the next unconsumed KEY_TYPED for the claimed press is still swallowed.
                val alreadyConsumedTyped =
                    AwtKeyEvent(childFocusOwner, AwtKeyEvent.KEY_TYPED, 0L, 0, AwtKeyEvent.VK_UNDEFINED, 'c').apply {
                        consume()
                    }
                fm.dispatchEvent(alreadyConsumedTyped)
                val unconsumedTyped =
                    AwtKeyEvent(childFocusOwner, AwtKeyEvent.KEY_TYPED, 0L, 0, AwtKeyEvent.VK_UNDEFINED, 'c')
                fm.dispatchEvent(unconsumedTyped)
                assertTrue(
                    "already-consumed KEY_TYPED must not discharge typed suppression before the real KEY_TYPED",
                    unconsumedTyped.isConsumed,
                )

                // A consumed KEY_RELEASED (e.g. IdeKeyEventDispatcher consuming Alt release) must still re-arm.
                val consumedRelease =
                    AwtKeyEvent(
                            childFocusOwner,
                            AwtKeyEvent.KEY_RELEASED,
                            0L,
                            InputEvent.CTRL_DOWN_MASK,
                            AwtKeyEvent.VK_C,
                            AwtKeyEvent.CHAR_UNDEFINED,
                        )
                        .apply { consume() }
                fm.dispatchEvent(consumedRelease)

                val secondPress =
                    AwtKeyEvent(
                        childFocusOwner,
                        AwtKeyEvent.KEY_PRESSED,
                        0L,
                        InputEvent.CTRL_DOWN_MASK,
                        AwtKeyEvent.VK_C,
                        AwtKeyEvent.CHAR_UNDEFINED,
                    )
                fm.dispatchEvent(secondPress)
                assertEquals("consumed KEY_RELEASED must still clear OnceUntilRelease latch", 2, claimHits)
            } finally {
                removeLane()
                KeyboardFocusManager.setCurrentKeyboardFocusManager(prevManager)
            }
        }
    }
}
