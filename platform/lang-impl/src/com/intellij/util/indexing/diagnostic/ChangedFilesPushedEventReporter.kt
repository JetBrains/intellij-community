// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.indexing.diagnostic

import com.intellij.openapi.project.Project
import org.jetbrains.annotations.ApiStatus

/**
 * Records a finished [ChangedFilesPushingStatistics] event for the index diagnostics dump.
 *
 * The default implementation ([NoopChangedFilesPushedEventReporter]) does nothing. The real implementation lives in the
 * `intellij.platform.lang.impl.backend` module and delegates to `ChangedFilesPushedDiagnostic.addEvent`. This indirection keeps
 * `intellij.platform.lang.impl` (in particular [com.intellij.openapi.roots.impl.PushedFilePropertiesUpdaterImpl]) free
 * of a direct dependency on the indexing backend implementation, which is a prerequisite for a frontend-only (IJ Light)
 * IDE where the backend module is not loaded.
 */
@ApiStatus.Internal
interface ChangedFilesPushedEventReporter {
  fun addEvent(project: Project, event: ChangedFilesPushingStatistics)
}

internal class NoopChangedFilesPushedEventReporter : ChangedFilesPushedEventReporter {
  override fun addEvent(project: Project, event: ChangedFilesPushingStatistics) {
  }
}
