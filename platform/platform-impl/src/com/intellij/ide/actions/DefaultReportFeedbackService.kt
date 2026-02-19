// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.actions

import com.intellij.ide.IdeBundle
import com.intellij.ide.logsUploader.LogPacker
import com.intellij.ide.logsUploader.LogUploader
import com.intellij.openapi.project.Project
import com.intellij.platform.ide.progress.withBackgroundProgress
import com.intellij.platform.util.progress.reportSequentialProgress
import kotlinx.coroutines.CoroutineScope

internal class DefaultReportFeedbackService(override val coroutineScope: CoroutineScope): ReportFeedbackService {
  override suspend fun collectLogs(project: Project): String =
    withBackgroundProgress(project, IdeBundle.message("reportProblemAction.progress.title.submitting"), true) {
      val id = reportSequentialProgress(size = 2) { reporter ->
        val file = reporter.itemStep {
          LogPacker.packLogs(project)
        }
        reporter.itemStep {
          LogUploader.uploadFile(file)
        }
      }
      LogUploader.getBrowseUrl(id)
    }
}
