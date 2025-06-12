// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jewel.bridge.shortcut

import com.intellij.openapi.actionSystem.KeyboardShortcut
import com.intellij.openapi.keymap.KeymapUtil
import javax.swing.KeyStroke
import org.jetbrains.jewel.ui.MenuItemShortcutProvider
import org.jetbrains.jewel.ui.component.ContextMenuItemActionType

public object BridgeMenuItemShortcutProvider : MenuItemShortcutProvider {
    override fun getShortcutKeyStroke(contextMenuItemActionType: ContextMenuItemActionType): KeyStroke? {
        val intellijActionId = labelToIntelliJActionIdMap[contextMenuItemActionType] ?: contextMenuItemActionType.name
        return KeymapUtil.getActiveKeymapShortcuts(intellijActionId)
            .shortcuts
            .filterIsInstance<KeyboardShortcut>()
            .firstOrNull()
            ?.firstKeyStroke
    }
}
