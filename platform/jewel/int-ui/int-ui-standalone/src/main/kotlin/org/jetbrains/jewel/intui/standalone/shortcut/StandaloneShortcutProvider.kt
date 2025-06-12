// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jewel.intui.standalone.shortcut

import java.awt.event.InputEvent
import java.awt.event.KeyEvent
import javax.swing.KeyStroke
import org.jetbrains.jewel.ui.MenuItemShortcutProvider
import org.jetbrains.jewel.ui.component.ContextMenuItemActionType
import org.jetbrains.skiko.hostOs

public object StandaloneShortcutProvider : MenuItemShortcutProvider {
    override fun getShortcutKeyStroke(contextMenuItemActionType: ContextMenuItemActionType): KeyStroke? =
        labelToShortcutMap[contextMenuItemActionType]
}

internal val labelToShortcutMap =
    mapOf(
        ContextMenuItemActionType.COPY to KeyStroke.getKeyStroke(KeyEvent.VK_C, getPrimaryMenuModifierMask()),
        ContextMenuItemActionType.PASTE to KeyStroke.getKeyStroke(KeyEvent.VK_V, getPrimaryMenuModifierMask()),
        ContextMenuItemActionType.CUT to KeyStroke.getKeyStroke(KeyEvent.VK_X, getPrimaryMenuModifierMask()),
        ContextMenuItemActionType.SELECT_ALL to KeyStroke.getKeyStroke(KeyEvent.VK_A, getPrimaryMenuModifierMask()),
    )

private fun getPrimaryMenuModifierMask(): Int {
    return if (hostOs.isMacOS) {
        InputEvent.META_DOWN_MASK
    } else {
        InputEvent.CTRL_DOWN_MASK
    }
}
