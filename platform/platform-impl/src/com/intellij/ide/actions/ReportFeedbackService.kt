// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.actions

import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import kotlinx.coroutines.CoroutineScope
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
interface ReportFeedbackService {
  val coroutineScope: CoroutineScope

  suspend fun collectLogs(project: Project): String?

  companion object {
    @JvmStatic
    fun getInstance(): ReportFeedbackService = service()
  }
}
