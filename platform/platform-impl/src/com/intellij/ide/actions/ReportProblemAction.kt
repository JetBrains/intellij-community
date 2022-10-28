// Copyright 2000-2022 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.actions

import com.intellij.ide.IdeBundle
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.ex.ApplicationInfoEx
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.Project

class ReportProblemAction : DumbAwareAction() {
  companion object {
    fun submit(project: Project?) {
      val appInfo = ApplicationInfoEx.getInstanceEx()
      ProgressManager.getInstance().run(object : Task.Backgroundable(project,
                                                                     IdeBundle.message("reportProblemAction.progress.title.submitting"), true) {
        override fun run(indicator: ProgressIndicator) {
          SendFeedbackAction.submit(project, appInfo.youtrackUrl, SendFeedbackAction.getDescription(project))
        }
      })
    }
  }
  override fun update(e: AnActionEvent) {
    val info = ApplicationInfoEx.getInstanceEx()
    e.presentation.isEnabledAndVisible = info != null && info.youtrackUrl != null
  }

  override fun getActionUpdateThread() = ActionUpdateThread.BGT

  override fun actionPerformed(e: AnActionEvent) {
    submit(e.project)
  }
}