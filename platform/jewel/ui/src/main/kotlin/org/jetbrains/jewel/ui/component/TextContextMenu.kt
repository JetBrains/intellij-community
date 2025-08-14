// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jewel.ui.component

import androidx.compose.foundation.ContextMenuItem
import androidx.compose.foundation.ContextMenuState
import androidx.compose.foundation.text.TextContextMenu
import androidx.compose.foundation.text.TextContextMenuArea
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalLocalization
import org.jetbrains.jewel.ui.component.ContextMenuItemOptionAction.CopyMenuItemOptionAction
import org.jetbrains.jewel.ui.component.ContextMenuItemOptionAction.CutMenuItemOptionAction
import org.jetbrains.jewel.ui.component.ContextMenuItemOptionAction.PasteMenuItemOptionAction
import org.jetbrains.jewel.ui.component.ContextMenuItemOptionAction.SelectAllMenuItemOptionAction
import org.jetbrains.jewel.ui.icons.AllIconsKeys

public object TextContextMenu : TextContextMenu {
    @Composable
    override fun Area(
        textManager: TextContextMenu.TextManager,
        state: ContextMenuState,
        content: @Composable (() -> Unit),
    ) {
        val localization = LocalLocalization.current
        val items: () -> List<ContextMenuItem> =
            remember(textManager, state) {
                {
                    listOfNotNull(
                        textManager.cut?.let {
                            ContextMenuItemOption(
                                icon = AllIconsKeys.Actions.MenuCut,
                                actionType = CutMenuItemOptionAction,
                                label = localization.cut,
                                action = it,
                            )
                        },
                        textManager.copy?.let {
                            ContextMenuItemOption(
                                icon = AllIconsKeys.Actions.Copy,
                                actionType = CopyMenuItemOptionAction,
                                label = localization.copy,
                                action = it,
                            )
                        },
                        textManager.paste?.let {
                            ContextMenuItemOption(
                                icon = AllIconsKeys.Actions.MenuPaste,
                                actionType = PasteMenuItemOptionAction,
                                label = localization.paste,
                                action = it,
                            )
                        },
                        textManager.selectAll?.let {
                            ContextMenuItemOption(
                                actionType = SelectAllMenuItemOptionAction,
                                label = localization.selectAll,
                                action = it,
                            )
                        },
                    )
                }
            }

        TextContextMenuArea(textManager, items, state, content)
    }
}
