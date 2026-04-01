package com.intellij.database.actions

import com.intellij.database.run.ui.ArrayGridViewer
import com.intellij.database.run.ui.ValueTabInfoProvider
import com.intellij.database.run.ui.findEditMaximized
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.ToggleAction
import com.intellij.openapi.project.DumbAware

class ToggleHideDeletedInArrayAction : ToggleAction(), DumbAware {
  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

  override fun update(e: AnActionEvent) {
    super.update(e)
    e.presentation.isEnabledAndVisible = arrayViewer(e) != null
  }

  override fun isSelected(e: AnActionEvent): Boolean = arrayViewer(e)?.hideDeleted ?: false

  override fun setSelected(e: AnActionEvent, state: Boolean) {
    arrayViewer(e)?.setHideDeleted(state)
  }
}

private fun arrayViewer(e: AnActionEvent): ArrayGridViewer? {
  val provider = findEditMaximized(e.dataContext)
    ?.getCurrentTabInfoProvider() as? ValueTabInfoProvider ?: return null
  return provider.getViewer() as? ArrayGridViewer
}
