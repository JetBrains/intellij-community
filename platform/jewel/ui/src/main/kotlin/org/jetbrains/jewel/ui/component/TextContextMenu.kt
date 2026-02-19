// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jewel.ui.component

import androidx.compose.foundation.ContextMenuItem
import androidx.compose.foundation.ContextMenuState
import androidx.compose.foundation.text.TextContextMenu
import androidx.compose.foundation.text.TextContextMenuArea
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalLocalization
import java.awt.GraphicsEnvironment
import org.jetbrains.jewel.ui.component.ContextMenuItemOptionAction.CopyMenuItemOptionAction
import org.jetbrains.jewel.ui.component.ContextMenuItemOptionAction.CutMenuItemOptionAction
import org.jetbrains.jewel.ui.component.ContextMenuItemOptionAction.PasteMenuItemOptionAction
import org.jetbrains.jewel.ui.component.ContextMenuItemOptionAction.SelectAllMenuItemOptionAction
import org.jetbrains.jewel.ui.icons.AllIconsKeys

/**
 * Jewel's implementation of [TextContextMenu], used to show a context menu for text selections both in
 * [`SelectionContainer`][androidx.compose.foundation.text.selection.SelectionContainer] and in
 * [`BasicTextField`][androidx.compose.foundation.text.BasicTextField].
 */
public object TextContextMenu : TextContextMenu {
    @Composable
    override fun Area(
        textManager: TextContextMenu.TextManager,
        state: ContextMenuState,
        content: @Composable (() -> Unit),
    ) {
        val localization = LocalLocalization.current
        val items: () -> List<ContextMenuItem> =
            remember(textManager, state, localization) {
                {
                    val cutAction =
                        textManager.cut?.let {
                            ContextMenuItemOption(
                                icon = AllIconsKeys.Actions.MenuCut,
                                actionType = CutMenuItemOptionAction,
                                enabled = textManager.selectedText.isNotEmpty(),
                                label = localization.cut,
                                action = it,
                            )
                        }

                    val copyAction =
                        textManager.copy?.let {
                            ContextMenuItemOption(
                                icon = AllIconsKeys.Actions.Copy,
                                actionType = CopyMenuItemOptionAction,
                                enabled = textManager.selectedText.isNotEmpty(),
                                label = localization.copy,
                                action = it,
                            )
                        }

                    // Paste is always visible and enabled in Swing, even when there is nothing to paste, but we
                    // can't do it because we have no way to tell if we're in a read-only context (e.g., in a
                    // SelectionContainer, or a read-only BasicTextField).
                    val pasteAction =
                        if (!GraphicsEnvironment.isHeadless()) {
                            textManager.paste?.let {
                                ContextMenuItemOption(
                                    icon = AllIconsKeys.Actions.MenuPaste,
                                    actionType = PasteMenuItemOptionAction,
                                    label = localization.paste,
                                    action = it,
                                )
                            }
                        } else {
                            // When in headless mode, the clipboard is not available, and some CMP
                            // internals throw an exception when trying to access it. This prevents
                            // the issue, letting us run unit tests, until CMP-9335 is fixed.
                            // TODO(CMP-9335) remove this guard once the upstream issue is fixed
                            null
                        }

                    val selectAllAction =
                        textManager.selectAll?.let {
                            ContextMenuItemOption(
                                actionType = SelectAllMenuItemOptionAction,
                                label = localization.selectAll,
                                action = it,
                            )
                        }

                    buildList {
                        if (cutAction != null) add(cutAction)
                        if (copyAction != null) add(copyAction)
                        if (pasteAction != null) add(pasteAction)

                        val anyActionInFirstGroup = cutAction != null || copyAction != null || pasteAction != null
                        if (selectAllAction != null && anyActionInFirstGroup) {
                            add(ContextMenuDivider)
                        }

                        if (selectAllAction != null) add(selectAllAction)
                    }
                }
            }

        TextContextMenuArea(textManager, items, state, content)
    }
}
