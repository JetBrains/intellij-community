// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.indexing.diagnostic.dto

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.intellij.util.indexing.diagnostic.IndexDiagnosticDumper
import com.intellij.util.indexing.diagnostic.ProjectIndexingActivityHistory
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

@JsonIgnoreProperties(ignoreUnknown = true)
data class JsonIndexingActivityDiagnostic(
  val appInfo: JsonIndexDiagnosticAppInfo = JsonIndexDiagnosticAppInfo(),
  val runtimeInfo: JsonRuntimeInfo = JsonRuntimeInfo(),
  val type: IndexDiagnosticDumper.IndexingActivityType,
  val projectIndexingActivityHistory: JsonProjectIndexingActivityHistory) {

  constructor(projectIndexingActivityHistory: ProjectIndexingActivityHistory) : this(JsonIndexDiagnosticAppInfo.create(),
                                                                                     JsonRuntimeInfo.create(),
                                                                                     projectIndexingActivityHistory.type,
                                                                                     projectIndexingActivityHistory.toJson())
}