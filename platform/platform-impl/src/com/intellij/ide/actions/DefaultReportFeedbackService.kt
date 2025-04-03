// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.actions

import com.intellij.ide.IdeBundle
import com.intellij.ide.logsUploader.LogPacker
import com.intellij.ide.logsUploader.LogUploader
import com.intellij.openapi.progress.checkCanceled
import com.intellij.openapi.project.Project
import com.intellij.platform.ide.progress.withBackgroundProgress
import com.intellij.platform.util.progress.reportProgress
import kotlinx.coroutines.CoroutineScope

internal class DefaultReportFeedbackService(override val coroutineScope: CoroutineScope): ReportFeedbackService {
  override suspend fun collectLogs(project: Project): String? =
    withBackgroundProgress(project, IdeBundle.message("reportProblemAction.progress.title.submitting"), true) {
      val id = reportProgress { reporter ->
        reporter.indeterminateStep("") {
          val file = LogPacker.packLogs(project)
          checkCanceled()
          val id = LogUploader.uploadFile(file)
          LogUploader.notify(project, id)
          id
        }
      }
      LogUploader.getBrowseUrl(id)
    }
}
