// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.lang.impl.backend

import com.intellij.openapi.project.Project
import com.intellij.util.indexing.diagnostic.ChangedFilesPushedDiagnostic
import com.intellij.util.indexing.diagnostic.ChangedFilesPushedEventReporter
import com.intellij.util.indexing.diagnostic.ChangedFilesPushingStatistics

internal class BackendChangedFilesPushedEventReporter : ChangedFilesPushedEventReporter {
  override fun addEvent(project: Project, event: ChangedFilesPushingStatistics) {
    ChangedFilesPushedDiagnostic.addEvent(project, event)
  }
}
