// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.actions

import com.intellij.ide.ui.UISettings
import com.intellij.idea.ActionsBundle
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.wm.impl.IdeBackgroundUtil
import com.intellij.util.ui.UIUtil
import com.intellij.util.ui.tree.TreeUtil
import org.jetbrains.annotations.ApiStatus.Internal
import java.awt.Window
import javax.swing.JTree

@Internal
class ShowHideDebugInfoInUiAction : DumbAwareAction() {
  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

  override fun update(e: AnActionEvent) {
    e.presentation.text = if (UISettings.getInstance().showInplaceCommentsInternal) {
      ActionsBundle.message("action.ShowHideDebugInfoInUi.text")
    }
    else {
      ActionsBundle.message("action.ShowHideDebugInfoInUi.show.text")
    }
  }

  override fun actionPerformed(e: AnActionEvent) {
    val uiSettings = UISettings.getInstance()
    uiSettings.showInplaceCommentsInternal = !uiSettings.showInplaceCommentsInternal
    for (tree in UIUtil.uiTraverser(null).withRoots(*Window.getWindows()).filter(JTree::class.java)) {
      TreeUtil.invalidateCacheAndRepaint(tree.ui)
    }
    IdeBackgroundUtil.repaintAllWindows()
  }
}
