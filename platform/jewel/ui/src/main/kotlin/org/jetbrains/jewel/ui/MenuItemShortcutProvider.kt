// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jewel.ui

import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.staticCompositionLocalOf
import java.awt.event.InputEvent
import java.awt.event.KeyEvent
import javax.swing.KeyStroke
import org.jetbrains.jewel.ui.component.ContextMenuItemActionType
import org.jetbrains.skiko.hostOs

/** Provides a way to also get the actual KeyStroke of a shortcut besides its hint */
public interface MenuItemShortcutProvider {
    /**
     * Gets the formatted shortcut string for the given action identifier.
     *
     * @param contextMenuItemActionType The action type (e.g., "Copy" or "Paste").
     * @return The human-readable shortcut string (e.g., "⌘C", "Ctrl + S"), or empty if no shortcut is defined or it
     *   shouldn't be displayed.
     */
    public fun getShortcutHint(contextMenuItemActionType: ContextMenuItemActionType): String

    /**
     * Gets the KeyStroke for a given action identifier.
     *
     * @param contextMenuItemActionType A type of action to be performed (e.g., ContextMenuItemActionType.COPY).
     */
    public fun getShortcutKeyStroke(contextMenuItemActionType: ContextMenuItemActionType): KeyStroke?
}

public object CommonShortcutHintProvider : MenuItemShortcutProvider {
    private fun getPrimaryMenuModifierMask(): Int {
        return if (hostOs.isMacOS) {
            InputEvent.META_DOWN_MASK
        } else {
            InputEvent.CTRL_DOWN_MASK
        }
    }

    private val labelToShortcutMap =
        mapOf(
            ContextMenuItemActionType.COPY to KeyStroke.getKeyStroke(KeyEvent.VK_C, getPrimaryMenuModifierMask()),
            ContextMenuItemActionType.PASTE to KeyStroke.getKeyStroke(KeyEvent.VK_V, getPrimaryMenuModifierMask()),
            ContextMenuItemActionType.CUT to KeyStroke.getKeyStroke(KeyEvent.VK_X, getPrimaryMenuModifierMask()),
            ContextMenuItemActionType.SELECT_ALL to KeyStroke.getKeyStroke(KeyEvent.VK_A, getPrimaryMenuModifierMask()),
        )

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

    override fun getShortcutHint(contextMenuItemActionType: ContextMenuItemActionType): String {
        val keyStroke = labelToShortcutMap[contextMenuItemActionType]
        return getKeyStrokeText(keyStroke)
    }

    override fun getShortcutKeyStroke(contextMenuItemActionType: ContextMenuItemActionType): KeyStroke? =
        labelToShortcutMap[contextMenuItemActionType]
}

public val LocalMenuItemShortcut: ProvidableCompositionLocal<MenuItemShortcutProvider> = staticCompositionLocalOf {
    error("No LocalMenuItemShortcut provided. Have you forgotten the theme?")
}
