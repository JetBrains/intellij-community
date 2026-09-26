// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.indexing.diagnostic.dto

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.intellij.util.indexing.diagnostic.IndexStatisticGroup
import com.intellij.util.indexing.diagnostic.ProjectIndexingActivityHistory

@JsonIgnoreProperties(ignoreUnknown = true)
data class JsonIndexingActivityDiagnostic(
  val appInfo: JsonIndexDiagnosticAppInfo = JsonIndexDiagnosticAppInfo(),
  val runtimeInfo: JsonRuntimeInfo = JsonRuntimeInfo(),
  val type: IndexStatisticGroup.IndexingActivityType = IndexStatisticGroup.IndexingActivityType.Scanning,
  val projectIndexingActivityHistory: JsonProjectIndexingActivityHistory = JsonProjectScanningHistory()) {

  constructor(projectIndexingActivityHistory: ProjectIndexingActivityHistory) : this(JsonIndexDiagnosticAppInfo.create(),
                                                                                     JsonRuntimeInfo.create(),
                                                                                     projectIndexingActivityHistory.type,
                                                                                     projectIndexingActivityHistory.toJson())
}