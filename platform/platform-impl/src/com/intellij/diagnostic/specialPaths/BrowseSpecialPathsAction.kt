package com.jetbrains.rider.diagnostics

import com.intellij.ide.actions.RevealFileAction
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAware

class BrowseSpecialPathsAction : AnAction(), DumbAware {
  override fun actionPerformed(e: AnActionEvent) {
    BrowseSpecialPathsDialog(e.project).show()
  }

  override fun getActionUpdateThread() = ActionUpdateThread.BGT

  override fun update(e: AnActionEvent) {
    e.presentation.isVisible = RevealFileAction.isSupported()
  }
}