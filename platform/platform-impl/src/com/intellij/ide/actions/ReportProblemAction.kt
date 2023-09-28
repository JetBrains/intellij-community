// Copyright 2000-2022 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.actions

import com.intellij.ide.BrowserUtil
import com.intellij.ide.IdeBundle
import com.intellij.ide.logsUploader.LogsPacker
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.ApplicationNamesInfo
import com.intellij.openapi.components.service
import com.intellij.openapi.progress.withBackgroundProgress
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.MessageDialogBuilder
import com.intellij.platform.ide.customization.ExternalProductResourceUrls
import kotlinx.coroutines.launch

open class ReportProblemAction : DumbAwareAction() {
  object Handler {
    fun submit(project: Project?) {
      if (project == null) return
      val uploadLogs = confirmLogsUploading(project)
      service<ReportFeedbackService>().coroutineScope.launch {
        withBackgroundProgress(project, IdeBundle.message("reportProblemAction.progress.title.submitting"), true) {
          val uploadedLogFolderName = if (uploadLogs) LogsPacker.uploadLogs(project) else null
          val url = ExternalProductResourceUrls.getInstance().bugReportUrl
          if (url != null) {
            var description = SendFeedbackAction.getDescription(project)
            if (uploadedLogFolderName != null) {
              description += "\nAuto-uploaded logs URL (accessible to JetBrains employees only): ${
                LogsPacker.getBrowseUrl(uploadedLogFolderName)
              }"
            }
            BrowserUtil.browse(url(description).toExternalForm(), project)
          }
        }
      }
    }

    private fun confirmLogsUploading(project: Project?): Boolean = MessageDialogBuilder.yesNo(
      IdeBundle.message("reportProblemAction.upload.logs.title"),
      IdeBundle.message("reportProblemAction.upload.logs.message", ApplicationNamesInfo.getInstance().fullProductName)
    ).ask(project)
  }

  override fun update(e: AnActionEvent) {
    e.presentation.isEnabledAndVisible = ExternalProductResourceUrls.getInstance().bugReportUrl != null
  }

  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

  override fun actionPerformed(e: AnActionEvent) {
    Handler.submit(e.project)
  }
}