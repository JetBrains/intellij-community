// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.actions

import com.intellij.ide.IdeBundle
import com.intellij.ide.logsUploader.LogsPacker
import com.intellij.openapi.project.Project
import com.intellij.platform.ide.progress.withBackgroundProgress
import kotlinx.coroutines.CoroutineScope

class DefaultReportFeedbackService(override val coroutineScope: CoroutineScope): ReportFeedbackService {

  override suspend fun collectLogs(project: Project?): String? {
    if (project == null) return null
    return withBackgroundProgress(project, IdeBundle.message("reportProblemAction.progress.title.submitting"), true) {
      LogsPacker.getBrowseUrl(LogsPacker.uploadLogs(project))
    }
  }
}