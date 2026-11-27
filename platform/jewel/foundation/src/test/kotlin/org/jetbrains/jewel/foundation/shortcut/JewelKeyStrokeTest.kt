// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jewel.foundation.shortcut

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.key
import java.awt.event.InputEvent
import java.awt.event.KeyEvent as AwtKeyEvent
import javax.swing.JPanel
import javax.swing.KeyStroke
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pins the [JewelKeyStroke.fromKeyDownOrNull] contract that a key-down which can never form a binding is dropped rather
 * than recorded. macOS `fn` is the motivating case: it arrives as [Key.Unknown] (VK_UNDEFINED), and a keymap recorder
 * that took it verbatim would bind the garbage stroke "Unknown keyCode: 0x0". Also verifies numpad location
 * normalization for keys shared with the main keyboard.
 */
internal class JewelKeyStrokeTest {
    private val source = JPanel()

    private fun awtKeyDown(
        keyCode: Int,
        keyChar: Char = AwtKeyEvent.CHAR_UNDEFINED,
        modifiersEx: Int = 0,
        keyLocation: Int = AwtKeyEvent.KEY_LOCATION_STANDARD,
    ): AwtKeyEvent = AwtKeyEvent(source, AwtKeyEvent.KEY_PRESSED, 0L, modifiersEx, keyCode, keyChar, keyLocation)

    @Test
    fun `an unmappable key-down (VK_UNDEFINED, as macOS fn produces) is dropped`() {
        val event = awtKeyDown(AwtKeyEvent.VK_UNDEFINED).toComposeKeyEvent()
        assertEquals("VK_UNDEFINED should map to Key.Unknown", Key.Unknown, event.key)
        assertNull("An unmappable key-down must not become a stroke", JewelKeyStroke.fromKeyDownOrNull(event))
    }

    @Test
    fun `a mappable key-down still produces its stroke`() {
        val stroke = JewelKeyStroke.fromKeyDownOrNull(awtKeyDown(AwtKeyEvent.VK_A, 'a').toComposeKeyEvent())
        assertEquals(Key.A, stroke?.key)
    }

    @Test
    fun `numpad keys shared with the main keyboard normalize to standard location`() {
        val cases =
            listOf(
                AwtKeyEvent.VK_ENTER to Key.Enter,
                AwtKeyEvent.VK_UP to Key.DirectionUp,
                AwtKeyEvent.VK_DOWN to Key.DirectionDown,
                AwtKeyEvent.VK_HOME to Key.MoveHome,
                AwtKeyEvent.VK_DELETE to Key.Delete,
            )
        for ((vk, expectedKey) in cases) {
            val event =
                awtKeyDown(
                        keyCode = vk,
                        modifiersEx = InputEvent.CTRL_DOWN_MASK,
                        keyLocation = AwtKeyEvent.KEY_LOCATION_NUMPAD,
                    )
                    .toComposeKeyEvent()
            val expectedStroke = JewelKeyStroke(expectedKey, ctrl = true)
            assertEquals("vk=$vk", expectedStroke, JewelKeyStroke.fromKeyDownOrNull(event))
            assertTrue("matches() should normalize numpad vk=$vk", expectedStroke.matches(event))
        }
    }

    @Test
    fun `dedicated numpad digit keys remain distinct from main keyboard digits`() {
        val numpadZero =
            awtKeyDown(keyCode = AwtKeyEvent.VK_NUMPAD0, keyLocation = AwtKeyEvent.KEY_LOCATION_NUMPAD)
                .toComposeKeyEvent()
        val stroke = JewelKeyStroke.fromKeyDownOrNull(numpadZero)
        assertEquals(JewelKeyStroke(Key.NumPad0), stroke)
        assertNotEquals(JewelKeyStroke(Key.Zero), stroke)
    }

    @Test
    fun `shared-VK numpad Key constants equal their standard counterparts in equality, hashCode, displayText, and matches`() {
        val pairs =
            listOf(
                Key.NumPadEnter to Key.Enter,
                Key.NumPadDirectionUp to Key.DirectionUp,
                Key.NumPadDirectionDown to Key.DirectionDown,
                Key.NumPadDirectionLeft to Key.DirectionLeft,
                Key.NumPadDirectionRight to Key.DirectionRight,
                Key.NumPadMoveHome to Key.MoveHome,
                Key.NumPadMoveEnd to Key.MoveEnd,
                Key.NumPadPageUp to Key.PageUp,
                Key.NumPadPageDown to Key.PageDown,
                Key.NumPadInsert to Key.Insert,
                Key.NumPadDelete to Key.Delete,
            )
        for ((numpadKey, standardKey) in pairs) {
            val numpadStroke = JewelKeyStroke(numpadKey, ctrl = true)
            val standardStroke = JewelKeyStroke(standardKey, ctrl = true)
            assertEquals("equals for $numpadKey vs $standardKey", standardStroke, numpadStroke)
            assertEquals("hashCode for $numpadKey vs $standardKey", standardStroke.hashCode(), numpadStroke.hashCode())
            assertEquals(
                "displayText for $numpadKey vs $standardKey",
                standardStroke.displayText(),
                numpadStroke.displayText(),
            )
        }
        val numpadEnterEvent =
            awtKeyDown(
                    keyCode = AwtKeyEvent.VK_ENTER,
                    modifiersEx = InputEvent.CTRL_DOWN_MASK,
                    keyLocation = AwtKeyEvent.KEY_LOCATION_NUMPAD,
                )
                .toComposeKeyEvent()
        val standardEnterEvent =
            awtKeyDown(
                    keyCode = AwtKeyEvent.VK_ENTER,
                    modifiersEx = InputEvent.CTRL_DOWN_MASK,
                    keyLocation = AwtKeyEvent.KEY_LOCATION_STANDARD,
                )
                .toComposeKeyEvent()
        val authoredWithNumPadEnter = JewelKeyStroke(Key.NumPadEnter, ctrl = true)
        assertEquals(authoredWithNumPadEnter, JewelKeyStroke.fromKeyDownOrNull(numpadEnterEvent))
        assertEquals(authoredWithNumPadEnter, JewelKeyStroke.fromKeyDownOrNull(standardEnterEvent))
        assertTrue(authoredWithNumPadEnter.matches(numpadEnterEvent))
        assertTrue(authoredWithNumPadEnter.matches(standardEnterEvent))
    }

    @Test
    fun `swingKeyStrokeToJewelKeyStroke canonicalizes dedicated numpad VKs to match Compose NumPad constants`() {
        val addSwing = KeyStroke.getKeyStroke(AwtKeyEvent.VK_ADD, InputEvent.CTRL_DOWN_MASK)
        val subtractSwing = KeyStroke.getKeyStroke(AwtKeyEvent.VK_SUBTRACT, InputEvent.CTRL_DOWN_MASK)
        val numpadOneSwing = KeyStroke.getKeyStroke(AwtKeyEvent.VK_NUMPAD1, InputEvent.ALT_DOWN_MASK)

        val addJewel = swingKeyStrokeToJewelKeyStroke(addSwing)
        val subtractJewel = swingKeyStrokeToJewelKeyStroke(subtractSwing)
        val numpadOneJewel = swingKeyStrokeToJewelKeyStroke(numpadOneSwing)

        assertEquals(JewelKeyStroke(Key.NumPadAdd, ctrl = true), addJewel)
        assertEquals(JewelKeyStroke(Key.NumPadSubtract, ctrl = true), subtractJewel)
        assertEquals(JewelKeyStroke(Key.NumPad1, alt = true), numpadOneJewel)
        assertNotEquals(JewelKeyStroke(Key.One, alt = true), numpadOneJewel)

        val physicalNumPadAdd =
            awtKeyDown(
                    keyCode = AwtKeyEvent.VK_ADD,
                    modifiersEx = InputEvent.CTRL_DOWN_MASK,
                    keyLocation = AwtKeyEvent.KEY_LOCATION_NUMPAD,
                )
                .toComposeKeyEvent()
        assertEquals(addJewel, JewelKeyStroke.fromKeyDownOrNull(physicalNumPadAdd))
    }

    @Test
    fun `dedicated keypad arrow VKs canonicalize to numpad location and stay distinct from main arrows`() {
        val cases =
            listOf(
                AwtKeyEvent.VK_KP_UP to Key.DirectionUp,
                AwtKeyEvent.VK_KP_DOWN to Key.DirectionDown,
                AwtKeyEvent.VK_KP_LEFT to Key.DirectionLeft,
                AwtKeyEvent.VK_KP_RIGHT to Key.DirectionRight,
            )
        for ((vk, mainArrow) in cases) {
            val fromSwing = swingKeyStrokeToJewelKeyStroke(KeyStroke.getKeyStroke(vk, 0))
            val physicalKpArrow =
                awtKeyDown(keyCode = vk, keyLocation = AwtKeyEvent.KEY_LOCATION_NUMPAD).toComposeKeyEvent()
            val fromEvent = JewelKeyStroke.fromKeyDownOrNull(physicalKpArrow)

            assertEquals("Swing VK=$vk must match native KEY_LOCATION_NUMPAD event", fromEvent, fromSwing)
            assertNotEquals(
                "dedicated keypad arrow VK=$vk must stay distinct from main $mainArrow",
                JewelKeyStroke(mainArrow),
                fromSwing,
            )
        }
    }

    @Test
    @Suppress("DEPRECATION")
    fun `swingKeyStrokeToJewelKeyStroke rejects release-only, AltGraph, and mouse-button Swing KeyStrokes`() {
        val releaseStroke = KeyStroke.getKeyStroke(AwtKeyEvent.VK_A, 0, true)
        val altGraphDownStroke = KeyStroke.getKeyStroke(AwtKeyEvent.VK_A, InputEvent.ALT_GRAPH_DOWN_MASK)
        val altGraphLegacyStroke = KeyStroke.getKeyStroke(AwtKeyEvent.VK_A, InputEvent.ALT_GRAPH_MASK)
        val mouseButtonStroke =
            KeyStroke.getKeyStroke(AwtKeyEvent.VK_A, InputEvent.CTRL_DOWN_MASK or InputEvent.BUTTON1_DOWN_MASK)

        assertNull("release-only Swing KeyStroke must return null", swingKeyStrokeToJewelKeyStroke(releaseStroke))
        assertNull(
            "ALT_GRAPH_DOWN_MASK Swing KeyStroke must return null",
            swingKeyStrokeToJewelKeyStroke(altGraphDownStroke),
        )
        assertNull(
            "ALT_GRAPH_MASK Swing KeyStroke must return null",
            swingKeyStrokeToJewelKeyStroke(altGraphLegacyStroke),
        )
        assertNull(
            "mouse-button modifier Swing KeyStroke must return null",
            swingKeyStrokeToJewelKeyStroke(mouseButtonStroke),
        )
    }
}
