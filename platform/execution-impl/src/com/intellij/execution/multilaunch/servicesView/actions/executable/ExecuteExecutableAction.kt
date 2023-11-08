package com.intellij.execution.multilaunch.servicesView.actions.executable

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DataKey
import com.intellij.openapi.util.NlsActions
import com.intellij.execution.multilaunch.execution.ExecutionMode
import com.intellij.execution.multilaunch.execution.executables.Executable
import javax.swing.Icon

abstract class ExecuteExecutableAction(@NlsActions.ActionText text: String, icon: Icon, mode: ExecutionMode) : AnAction(text, null, icon) {
  companion object {
    val EXECUTABLE_KEY = DataKey.create<Executable>("EXECUTABLE")
  }

  override fun getActionUpdateThread() = ActionUpdateThread.BGT

  override fun actionPerformed(e: AnActionEvent) {
    val executable = e.getData(EXECUTABLE_KEY) ?: return
  }
}