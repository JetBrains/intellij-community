// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jewel.ui.component

import java.awt.Panel
import java.awt.event.InputEvent
import java.awt.event.KeyEvent as AwtKeyEvent
import javax.swing.KeyStroke
import org.jetbrains.jewel.foundation.shortcut.toComposeKeyEvent
import org.junit.Assert.assertEquals
import org.junit.Test

internal class ContextMenuKeyStrokeTest {
    @Test
    fun `composeKeyEventToSwingKeyStroke preserves key-release and AltGraph modifiers from AWT event`() {
        val releaseEvent =
            AwtKeyEvent(Panel(), AwtKeyEvent.KEY_RELEASED, 0L, 0, AwtKeyEvent.VK_C, AwtKeyEvent.CHAR_UNDEFINED)
                .toComposeKeyEvent()
        val altGraphEvent =
            AwtKeyEvent(
                    Panel(),
                    AwtKeyEvent.KEY_PRESSED,
                    0L,
                    InputEvent.ALT_GRAPH_DOWN_MASK,
                    AwtKeyEvent.VK_V,
                    AwtKeyEvent.CHAR_UNDEFINED,
                )
                .toComposeKeyEvent()

        assertEquals(KeyStroke.getKeyStroke(AwtKeyEvent.VK_C, 0, true), composeKeyEventToSwingKeyStroke(releaseEvent))
        assertEquals(
            KeyStroke.getKeyStroke(AwtKeyEvent.VK_V, InputEvent.ALT_GRAPH_DOWN_MASK, false),
            composeKeyEventToSwingKeyStroke(altGraphEvent),
        )
    }
}
