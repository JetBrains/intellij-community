// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.diagnostic

import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.process.ProcessOutput
import com.intellij.execution.util.ExecUtil.sudoAndGetOutput
import com.intellij.idea.ActionsBundle
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.ui.MessageDialogBuilder
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.util.SystemInfo
import com.intellij.util.Restarter
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
class FixWSLFirewallAction : DumbAwareAction() {
  override fun actionPerformed(e: AnActionEvent) {
    val result = MessageDialogBuilder
      .okCancel(ActionsBundle.actionText("FixWSLFirewall"), DiagnosticBundle.message("wsl.firewall.settings.prompt"))
      .ask(e.project)

    if (!result) return
    val starter = Restarter.getIdeStarter()

    val powershellCommand =
      """Get-NetFirewallApplicationFilter -Program "${starter}" | Get-NetFirewallRule | Where-Object Profile -eq "Public" | Get-NetFirewallPortFilter | Where-Object Protocol -eq "TCP" | Get-NetFirewallRule | Set-NetFirewallRule -Action Allow"""

    val output = try {
      ProgressManager.getInstance().run(object : Task.WithResult<ProcessOutput, Exception>(e.project, ActionsBundle.actionText("FixWSLFirewall"), false) {
        override fun compute(indicator: ProgressIndicator): ProcessOutput {
          return sudoAndGetOutput(GeneralCommandLine("powershell", "-Command", powershellCommand), "")
        }
      })
    }
    catch (e: Exception) {
      null
    }

    if (output?.exitCode == 0) {
      Messages.showMessageDialog(e.project, DiagnosticBundle.message("wsl.firewall.settings.success"), ActionsBundle.actionText("FixWSLFirewall"), null)
    }
    else {
      Messages.showErrorDialog(e.project, DiagnosticBundle.message("wsl.firewall.settings.failure"), ActionsBundle.actionText("FixWSLFirewall"))
    }
  }

  override fun update(e: AnActionEvent) {
    e.presentation.isEnabledAndVisible = SystemInfo.isWindows && Restarter.getIdeStarter() != null
  }

  override fun getActionUpdateThread(): ActionUpdateThread {
    return ActionUpdateThread.BGT
  }
}
