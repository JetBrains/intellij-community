// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jewel.bridge.menuShortcut

import com.intellij.openapi.actionSystem.KeyboardShortcut
import com.intellij.openapi.keymap.KeymapUtil
import javax.swing.KeyStroke
import org.jetbrains.jewel.ui.MenuItemShortcutProvider
import org.jetbrains.jewel.ui.component.ContextMenuItemOptionAction

internal object BridgeMenuItemShortcutProvider : MenuItemShortcutProvider {
    override fun getShortcutKeyStroke(actionType: ContextMenuItemOptionAction): KeyStroke? =
        KeymapUtil.getActiveKeymapShortcuts(actionTypeToIdeAction(actionType))
            .shortcuts
            .filterIsInstance<KeyboardShortcut>()
            .firstOrNull()
            ?.firstKeyStroke
}
