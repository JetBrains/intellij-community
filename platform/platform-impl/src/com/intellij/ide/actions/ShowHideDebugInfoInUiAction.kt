// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.actions

import com.intellij.ide.ui.UISettings
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAwareToggleAction
import com.intellij.openapi.wm.impl.IdeBackgroundUtil
import com.intellij.util.ui.UIUtil
import com.intellij.util.ui.tree.TreeUtil
import org.jetbrains.annotations.ApiStatus.Internal
import java.awt.Window
import javax.swing.JTree

@Internal
class ShowHideDebugInfoInUiAction : DumbAwareToggleAction() {
  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

  override fun isSelected(e: AnActionEvent): Boolean = UISettings.getInstance().showInplaceCommentsInternal

  override fun setSelected(e: AnActionEvent, state: Boolean) {
    val uiSettings = UISettings.getInstance()
    uiSettings.showInplaceCommentsInternal = state
    for (tree in UIUtil.uiTraverser(null).withRoots(*Window.getWindows()).filter(JTree::class.java)) {
      TreeUtil.invalidateCacheAndRepaint(tree.ui)
    }
    IdeBackgroundUtil.repaintAllWindows()
  }
}
