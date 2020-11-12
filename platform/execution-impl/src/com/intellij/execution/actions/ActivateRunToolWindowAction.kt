// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.actions

import com.intellij.execution.executors.DefaultRunExecutor
import com.intellij.execution.impl.EditConfigurationsDialog
import com.intellij.execution.ui.RunContentManager
import com.intellij.execution.ui.RunContentManagerImpl
import com.intellij.icons.AllIcons
import com.intellij.ide.actions.ToolWindowEmptyStateAction
import com.intellij.lang.LangBundle
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.help.HelpManager
import com.intellij.openapi.keymap.KeymapUtil
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindowId
import com.intellij.ui.SimpleTextAttributes
import com.intellij.util.ui.StatusText

class ActivateRunToolWindowAction : ToolWindowEmptyStateAction(ToolWindowId.RUN, AllIcons.Toolwindows.ToolWindowRun) {
  override fun ensureToolWindowCreated(project: Project) {
    val runContentManager = RunContentManager.getInstance(project) as RunContentManagerImpl
    runContentManager.registerToolWindow(DefaultRunExecutor.getRunExecutorInstance())
  }

  override fun setupEmptyText(project: Project, text: StatusText) {
    text.isCenterAlignText = false
    text.appendLine(LangBundle.message("run.toolwindow.empty.text.0"))
    text.appendLine(LangBundle.message("run.toolwindow.empty.text.1"))
    text.appendLine(LangBundle.message("run.toolwindow.empty.text.2"))

    appendLaunchConfigurationText(text, project, "ChooseRunConfiguration")
    text.appendLine("")
    text.appendLine(AllIcons.General.ContextHelp, LangBundle.message("run.toolwindow.empty.text.help"), SimpleTextAttributes.LINK_PLAIN_ATTRIBUTES) {
      HelpManager.getInstance().invokeHelp("procedures.running.run")
    }
  }

  companion object {
    fun appendLaunchConfigurationText(text: StatusText, project: Project, actionId: String) {
      val shortcut = ActionManager.getInstance().getKeyboardShortcut(actionId)
      val shortcutText = shortcut?.let { " (${KeymapUtil.getShortcutText(shortcut)})" } ?: ""
      val line = LangBundle.message("run.toolwindow.empty.text.3", shortcutText)
      text.appendLine(line.substringBefore('<'))
      text.appendText(line.substringAfter('<').substringBefore('>'), SimpleTextAttributes.LINK_PLAIN_ATTRIBUTES) {
        EditConfigurationsDialog(project).show()
      }
      text.appendText(line.substringAfter('>'))
    }
  }
}
