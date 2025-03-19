// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.actions

import com.intellij.ide.projectView.ProjectView
import com.intellij.ide.ui.UISettings.Companion.getInstance
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAwareToggleAction
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.wm.impl.IdeBackgroundUtil
import com.intellij.util.ui.UIUtil
import com.intellij.util.ui.tree.TreeUtil
import org.jetbrains.annotations.ApiStatus
import java.awt.Window
import javax.swing.JTree

/**
 * @author gregsh
 */
@ApiStatus.Internal
class ViewInplaceCommentsAction : DumbAwareToggleAction() {
  init {
    isEnabledInModalContext = true
  }

  override fun isSelected(e: AnActionEvent): Boolean {
    return getInstance().showInplaceComments
  }

  override fun setSelected(e: AnActionEvent, state: Boolean) {
    getInstance().showInplaceComments = state
    updateAllTreesCellsWidth()
    IdeBackgroundUtil.repaintAllWindows()
    updateProjectViews() // project views need BGT model update
  }

  override fun getActionUpdateThread(): ActionUpdateThread {
    return ActionUpdateThread.BGT
  }

  private fun updateAllTreesCellsWidth() {
    for (tree in UIUtil.uiTraverser(null).withRoots(*Window.getWindows()).filter(JTree::class.java)) {
      TreeUtil.invalidateCacheAndRepaint(tree.ui)
    }
  }

  private fun updateProjectViews() {
    ProjectManager.getInstance()?.openProjects?.forEach { project ->
      ProjectView.getInstance(project)?.currentProjectViewPane?.updateFromRoot(true)
    }
  }

}