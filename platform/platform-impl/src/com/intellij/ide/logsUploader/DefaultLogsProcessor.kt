// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.logsUploader

import com.intellij.openapi.application.PathManager
import com.intellij.openapi.project.Project

class DefaultLogsProcessor: LogsPreprocessor {
  override fun getLogsEntries(project: Project?): List<LogsPreprocessor.LogsEntry> {
    return listOf(LogsPreprocessor.LogsEntry("", listOf(PathManager.getLogDir().toFile())))
  }
}