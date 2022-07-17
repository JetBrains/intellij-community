// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.indexing.diagnostic.dto

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.intellij.util.indexing.diagnostic.ProjectIndexingHistoryImpl

@JsonIgnoreProperties(ignoreUnknown = true)
data class JsonIndexDiagnostic(
  val appInfo: JsonIndexDiagnosticAppInfo = JsonIndexDiagnosticAppInfo(),
  val runtimeInfo: JsonRuntimeInfo = JsonRuntimeInfo(),
  val projectIndexingHistory: JsonProjectIndexingHistory = JsonProjectIndexingHistory()
) {
  companion object {
    fun generateForHistory(projectIndexingHistory: ProjectIndexingHistoryImpl): JsonIndexDiagnostic =
      JsonIndexDiagnostic(
        JsonIndexDiagnosticAppInfo.create(),
        JsonRuntimeInfo.create(),
        projectIndexingHistory.toJson()
      )
  }
}