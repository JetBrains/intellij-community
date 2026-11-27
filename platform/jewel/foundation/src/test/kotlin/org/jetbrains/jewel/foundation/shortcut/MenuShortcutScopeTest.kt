// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jewel.foundation.shortcut

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.nativeKeyCode
import java.awt.event.InputEvent
import java.awt.event.KeyEvent as AwtKeyEvent
import javax.swing.JPanel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pins the menu-scope absorption contract: while a menu scope is open its strokes resolve ahead of ordinary dispatch on
 * the same host (one dispatcher, no racing), the innermost scope wins, matched strokes are consumed, and closing
 * restores ordinary dispatch untouched.
 *
 * The host has no composed root here, so ordinary dispatch never resolves anything — which is exactly the point: only
 * the menu scopes can consume.
 */
internal class MenuShortcutScopeTest {
    private val keymap = InMemoryJewelKeymap("test")
    private val host = JewelShortcutHostState { keymap }

    private val ctrlD = JewelKeyStroke(Key.D, ctrl = true)

    private val eventSource = JPanel()

    private fun keyDown(stroke: JewelKeyStroke): Boolean {
        var modifiers = 0
        if (stroke.ctrl) modifiers = modifiers or InputEvent.CTRL_DOWN_MASK
        if (stroke.shift) modifiers = modifiers or InputEvent.SHIFT_DOWN_MASK
        if (stroke.alt) modifiers = modifiers or InputEvent.ALT_DOWN_MASK
        if (stroke.meta) modifiers = modifiers or InputEvent.META_DOWN_MASK
        val awtEvent =
            AwtKeyEvent(
                eventSource,
                AwtKeyEvent.KEY_PRESSED,
                0L,
                modifiers,
                stroke.key.nativeKeyCode,
                AwtKeyEvent.CHAR_UNDEFINED,
            )
        return host.onPreviewKeyEvent(awtEvent.toComposeKeyEvent())
    }

    @Test
    fun `a registered stroke is consumed and invoked while the scope is open, not after`() {
        var invoked = 0
        val scope = host.openMenuShortcutScope()
        scope.register(ctrlD) { invoked++ }

        assertTrue(keyDown(ctrlD))
        assertEquals(1, invoked)

        scope.close()
        assertFalse("after close, the unbound stroke passes through", keyDown(ctrlD))
        assertEquals(1, invoked)
    }

    @Test
    fun `unregistered strokes pass through an open scope`() {
        val scope = host.openMenuShortcutScope()
        scope.register(ctrlD) {}
        assertFalse(keyDown(JewelKeyStroke(Key.E, ctrl = true)))
        scope.close()
    }

    @Test
    fun `the innermost open scope wins and closing it re-exposes the outer scope`() {
        var outer = 0
        var inner = 0
        val outerScope = host.openMenuShortcutScope()
        outerScope.register(ctrlD) { outer++ }
        val innerScope = host.openMenuShortcutScope()
        innerScope.register(ctrlD) { inner++ }

        assertTrue(keyDown(ctrlD))
        assertEquals(0, outer)
        assertEquals(1, inner)

        innerScope.close()
        assertTrue(keyDown(ctrlD))
        assertEquals(1, outer)

        outerScope.close()
    }

    @Test
    fun `replaceAll swaps the scope registrations wholesale`() {
        var first = 0
        var second = 0
        val scope = host.openMenuShortcutScope()
        scope.register(ctrlD) { first++ }
        scope.replaceAll(mapOf(JewelKeyStroke(Key.E, ctrl = true) to { second++ }))

        assertFalse(keyDown(ctrlD))
        assertTrue(keyDown(JewelKeyStroke(Key.E, ctrl = true)))
        assertEquals(0, first)
        assertEquals(1, second)
        scope.close()
    }

    @Test
    fun `onDispatch observes menu scope dispatch and trailing TypedSuppressed events`() {
        val decisions = mutableListOf<DispatchDecision.Consumed>()
        host.onDispatch = { decisions += it }
        var menuRuns = 0
        val scope = host.openMenuShortcutScope()
        try {
            scope.register(ctrlD) { menuRuns++ }
            assertTrue(keyDown(ctrlD))
            val typed =
                AwtKeyEvent(eventSource, AwtKeyEvent.KEY_TYPED, 0L, 0, AwtKeyEvent.VK_UNDEFINED, 'd')
                    .toComposeKeyEvent()
            assertTrue(host.onPreviewKeyEvent(typed))
            assertEquals(1, menuRuns)
            assertEquals(
                listOf(
                    DispatchDecision.Consumed(null, JewelKeySequence(ctrlD), DispatchDecision.Consumed.Route.Claim),
                    DispatchDecision.Consumed(null, null, DispatchDecision.Consumed.Route.TypedSuppressed),
                ),
                decisions,
            )
        } finally {
            scope.close()
            host.onDispatch = null
        }
    }
}
