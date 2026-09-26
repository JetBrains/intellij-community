// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jewel.intui.standalone.menuShortcut

import java.awt.event.KeyEvent
import javax.swing.KeyStroke
import org.jetbrains.jewel.ui.MenuItemShortcutHintProvider
import org.jetbrains.jewel.ui.component.ContextMenuItemOptionAction
import org.jetbrains.skiko.hostOs

internal object StandaloneMenuItemShortcutHintProvider : MenuItemShortcutHintProvider {
    override fun getShortcutHint(actionType: ContextMenuItemOptionAction): String =
        getKeyStrokeText(actionTypeToKeyStroke(actionType))

    private fun getKeyStrokeText(keyStroke: KeyStroke?): String {
        if (keyStroke == null) return ""

        val modifiers = keyStroke.modifiers
        var text = ""

        if (modifiers != 0) {
            text = KeyEvent.getModifiersExText(modifiers)
            if (text.isNotEmpty()) {
                text += if (!hostOs.isMacOS) "+" else ""
            }
        }

        val keyCode = keyStroke.keyCode
        if (keyCode != 0) {
            // KeyEvent.getKeyText() gives the textual representation of the key, e.g., "C", "F1", "Space"
            text += KeyEvent.getKeyText(keyCode)
        } else {
            // For keyChar-based KeyStrokes (less common for menu shortcuts)
            val keyChar = keyStroke.keyChar
            if (keyChar != KeyEvent.CHAR_UNDEFINED) {
                text += keyChar.uppercaseChar()
            }
        }
        return text
    }
}
