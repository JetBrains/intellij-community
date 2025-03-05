package com.intellij.database.actions

import com.intellij.database.DataGridBundle
import com.intellij.database.run.ui.*
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAwareAction

/**
 * @author Liudmila Kornilova
 */
class ToggleRecordViewLayoutAction : DumbAwareAction() {
  override fun getActionUpdateThread(): ActionUpdateThread {
    return ActionUpdateThread.BGT
  }

  override fun update(e: AnActionEvent) {
    super.update(e)
    val tab = getEditMaximizedTab(e)
    e.presentation.isEnabledAndVisible = tab != null
    tab?.let {
      e.presentation.text =
        if (it.getViewer().isTwoColumnsLayout) {
          DataGridBundle.message("action.Console.TableResult.EditMaximized.Record.ToggleLayout.text.toDisable")
        }
        else {
          DataGridBundle.message("action.Console.TableResult.EditMaximized.Record.ToggleLayout.text.toEnable")
        }
    }
  }

  override fun actionPerformed(e: AnActionEvent) {
    val tab = getEditMaximizedTab(e)
    tab?.let {
      it.getViewer().isTwoColumnsLayout = !it.getViewer().isTwoColumnsLayout
      it.update(UpdateEvent.SettingsChanged)
    }
  }

  companion object {
    private fun getEditMaximizedTab(e: AnActionEvent): RecordViewInfoProvider? {
      return findEditMaximized(e.dataContext)?.getCurrentTabInfoProvider() as? RecordViewInfoProvider
    }
  }
}
