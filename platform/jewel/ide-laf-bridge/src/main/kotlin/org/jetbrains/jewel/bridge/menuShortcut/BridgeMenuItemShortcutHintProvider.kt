// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jewel.bridge.menuShortcut

import com.intellij.openapi.actionSystem.IdeActions
import com.intellij.openapi.keymap.KeymapUtil
import org.jetbrains.jewel.ui.MenuItemShortcutHintProvider
import org.jetbrains.jewel.ui.component.ContextMenuItemOptionAction
import org.jetbrains.jewel.ui.component.ContextMenuItemOptionAction.CopyMenuItemOptionAction
import org.jetbrains.jewel.ui.component.ContextMenuItemOptionAction.CutMenuItemOptionAction
import org.jetbrains.jewel.ui.component.ContextMenuItemOptionAction.PasteMenuItemOptionAction
import org.jetbrains.jewel.ui.component.ContextMenuItemOptionAction.SelectAllMenuItemOptionAction

internal object BridgeMenuItemShortcutHintProvider : MenuItemShortcutHintProvider {
    override fun getShortcutHint(actionType: ContextMenuItemOptionAction): String =
        KeymapUtil.getShortcutText(actionTypeToIdeAction(actionType))
}

internal fun actionTypeToIdeAction(actionType: ContextMenuItemOptionAction) =
    when (actionType) {
        is CopyMenuItemOptionAction -> IdeActions.ACTION_COPY
        is PasteMenuItemOptionAction -> IdeActions.ACTION_PASTE
        is CutMenuItemOptionAction -> IdeActions.ACTION_CUT
        is SelectAllMenuItemOptionAction -> IdeActions.ACTION_SELECT_ALL
    }
