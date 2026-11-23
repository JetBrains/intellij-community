// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jewel.bridge.menuShortcut

import com.intellij.openapi.actionSystem.CustomShortcutSet
import com.intellij.openapi.actionSystem.KeyboardShortcut
import java.awt.event.InputEvent
import java.awt.event.KeyEvent
import javax.swing.KeyStroke
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

internal class BridgeMenuItemShortcutProviderTest {
    @Test
    fun `two-stroke chord shortcuts are skipped instead of truncating to their prefix stroke`() {
        val firstStroke = KeyStroke.getKeyStroke(KeyEvent.VK_K, InputEvent.CTRL_DOWN_MASK)
        val secondStroke = KeyStroke.getKeyStroke(KeyEvent.VK_X, InputEvent.CTRL_DOWN_MASK)
        val singleStroke = KeyStroke.getKeyStroke(KeyEvent.VK_X, InputEvent.CTRL_DOWN_MASK)
        val chordOnly = CustomShortcutSet(KeyboardShortcut(firstStroke, secondStroke))
        val chordThenSingle =
            CustomShortcutSet(KeyboardShortcut(firstStroke, secondStroke), KeyboardShortcut(singleStroke, null))

        assertNull(
            "2-stroke chord must not truncate to its first stroke",
            BridgeMenuItemShortcutProvider.firstSingleStrokeKeyStroke(chordOnly),
        )
        assertEquals(
            "single-stroke shortcut must be chosen ahead of ignoring a 2-stroke chord",
            singleStroke,
            BridgeMenuItemShortcutProvider.firstSingleStrokeKeyStroke(chordThenSingle),
        )
    }
}
