package com.intellij.database.actions

import com.intellij.database.DatabaseDataKeys
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAwareToggleAction
import com.intellij.ui.OnePixelSplitter
import java.awt.Container

/** In table*/
class SwitchToTableAction : DumbAwareToggleAction() {
  override fun getActionUpdateThread() = ActionUpdateThread.BGT

  override fun isSelected(e: AnActionEvent): Boolean {
    val parentComponent = getParentComponent(e) ?: return false
    return parentComponent !is OnePixelSplitter || parentComponent.firstComponent.isVisible
  }

  private fun getParentComponent(e: AnActionEvent): Container? {
    val grid = e.getData(DatabaseDataKeys.DATA_GRID_KEY) ?: return null
    return (grid.panel as? Container)?.parent
  }

  override fun setSelected(e: AnActionEvent, state: Boolean) {
    val parentComponent = getParentComponent(e) ?: return

    if (parentComponent is OnePixelSplitter) {
      parentComponent.firstComponent.isVisible = state
      parentComponent.secondComponent.isVisible = !state
    }
  }
}