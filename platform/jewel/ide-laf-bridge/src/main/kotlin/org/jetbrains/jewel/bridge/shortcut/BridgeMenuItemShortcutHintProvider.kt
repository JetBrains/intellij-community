// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jewel.bridge.shortcut

import com.intellij.openapi.actionSystem.IdeActions
import com.intellij.openapi.keymap.KeymapUtil
import org.jetbrains.jewel.ui.MenuItemShortcutHintProvider
import org.jetbrains.jewel.ui.component.ContextMenuItemActionType

public object BridgeMenuItemShortcutHintProvider : MenuItemShortcutHintProvider {
    override fun getShortcutHint(contextMenuItemActionType: ContextMenuItemActionType): String {
        val intellijActionId = labelToIntelliJActionIdMap[contextMenuItemActionType] ?: contextMenuItemActionType.name
        return KeymapUtil.getShortcutText(intellijActionId)
    }
}

internal val labelToIntelliJActionIdMap =
    mapOf(
        ContextMenuItemActionType.COPY to IdeActions.ACTION_COPY,
        ContextMenuItemActionType.PASTE to IdeActions.ACTION_PASTE,
        ContextMenuItemActionType.CUT to IdeActions.ACTION_CUT,
        ContextMenuItemActionType.SELECT_ALL to IdeActions.ACTION_SELECT_ALL,
    )
