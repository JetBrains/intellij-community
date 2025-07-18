// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jewel.intui.standalone.menuShortcut

import java.awt.event.InputEvent
import java.awt.event.KeyEvent
import javax.swing.KeyStroke
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.jewel.foundation.InternalJewelApi
import org.jetbrains.jewel.ui.MenuItemShortcutProvider
import org.jetbrains.jewel.ui.component.ContextMenuItemOptionAction
import org.jetbrains.jewel.ui.component.ContextMenuItemOptionAction.CopyMenuItemOptionAction
import org.jetbrains.jewel.ui.component.ContextMenuItemOptionAction.PasteMenuItemOptionAction
import org.jetbrains.jewel.ui.component.ContextMenuItemOptionAction.SelectAllMenuItemOptionAction
import org.jetbrains.skiko.hostOs

@ApiStatus.Internal
@InternalJewelApi
public object StandaloneShortcutProvider : MenuItemShortcutProvider {
    override fun getShortcutKeyStroke(actionType: ContextMenuItemOptionAction): KeyStroke? =
        actionTypeToKeyStroke(actionType)
}

internal fun actionTypeToKeyStroke(actionType: ContextMenuItemOptionAction) =
    when (actionType) {
        is CopyMenuItemOptionAction -> KeyStroke.getKeyStroke(KeyEvent.VK_C, getPrimaryMenuModifierMask())
        is PasteMenuItemOptionAction -> KeyStroke.getKeyStroke(KeyEvent.VK_V, getPrimaryMenuModifierMask())
        ContextMenuItemOptionAction.CutMenuItemOptionAction ->
            KeyStroke.getKeyStroke(KeyEvent.VK_X, getPrimaryMenuModifierMask())
        is SelectAllMenuItemOptionAction -> KeyStroke.getKeyStroke(KeyEvent.VK_A, getPrimaryMenuModifierMask())
    }

private fun getPrimaryMenuModifierMask(): Int {
    return if (hostOs.isMacOS) {
        InputEvent.META_DOWN_MASK
    } else {
        InputEvent.CTRL_DOWN_MASK
    }
}
