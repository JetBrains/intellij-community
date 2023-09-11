// Copyright 2000-2022 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.actions

import com.intellij.ide.BrowserUtil
import com.intellij.ide.IdeBundle
import com.intellij.ide.logsUploader.LogsPacker
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.ApplicationNamesInfo
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.MessageDialogBuilder
import com.intellij.platform.ide.customization.ExternalProductResourceUrls

open class ReportProblemAction : DumbAwareAction() {
  object Handler {
    fun submit(project: Project?) {
      val uploadLogs = confirmLogsUploading(project)

      ProgressManager.getInstance().run(object : Task.Backgroundable(project,
                                                                     IdeBundle.message("reportProblemAction.progress.title.submitting"), true) {
        override fun run(indicator: ProgressIndicator) {
          val uploadedLogFolderName = if (uploadLogs) LogsPacker.uploadLogs(indicator, project) else null
          val url = ExternalProductResourceUrls.getInstance().bugReportUrl
          if (url != null) {
            var description = SendFeedbackAction.getDescription(project)
            if (uploadedLogFolderName != null) {
              description += "\nAuto-uploaded logs URL (accessible to JetBrains employees only): ${LogsPacker.getBrowseUrl(uploadedLogFolderName)}"
            }

            BrowserUtil.browse(url(description).toExternalForm(), project)
          }
        }
      })
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