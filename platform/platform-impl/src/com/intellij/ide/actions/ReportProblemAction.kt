// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.actions

import com.intellij.ide.BrowserUtil
import com.intellij.ide.IdeBundle
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.ApplicationNamesInfo
import com.intellij.openapi.application.EDT
import com.intellij.openapi.components.service
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.MessageDialogBuilder
import com.intellij.platform.ide.customization.ExternalProductResourceUrls
import com.intellij.platform.ide.progress.withBackgroundProgress
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

internal class ReportProblemAction : DumbAwareAction() {
  override fun update(e: AnActionEvent) {
    e.presentation.isEnabledAndVisible = e.project != null && ExternalProductResourceUrls.getInstance().bugReportUrl != null
  }

  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

  override fun actionPerformed(e: AnActionEvent) {
    val project = e.project ?: return
    ExternalProductResourceUrls.getInstance().bugReportUrl?.let { url ->
      service<ReportFeedbackService>().coroutineScope.launch {
        withBackgroundProgress(project, IdeBundle.message("reportProblemAction.progress.title.submitting"), cancellable = true) {
          val description = getIssueDescription(project)
          BrowserUtil.browse(url(description).toExternalForm(), project)
        }
      }
    }
  }

  private suspend fun getIssueDescription(project: Project): String {
    val uploadLogs = withContext(Dispatchers.EDT) {
      confirmLogsUploading(project)
    }
    val sb = StringBuilder("\n\n")
    sb.append(SendFeedbackAction.getDescription(project))
    if (uploadLogs) {
      val url = ReportFeedbackService.getInstance().collectLogs(project)
      if (!url.isNullOrEmpty()) {
        sb.append("\nAuto-uploaded logs URL (accessible to JetBrains employees only): ${url}")
      }
    }
    return sb.toString()
  }

  private fun confirmLogsUploading(project: Project): Boolean = MessageDialogBuilder.yesNo(
    IdeBundle.message("reportProblemAction.upload.logs.title"),
    IdeBundle.message("reportProblemAction.upload.logs.message", ApplicationNamesInfo.getInstance().fullProductName)
  ).ask(project)
}
