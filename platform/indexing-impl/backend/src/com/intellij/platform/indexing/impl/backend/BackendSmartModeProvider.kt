// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.indexing.impl.backend

import com.intellij.openapi.components.serviceOrNull
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.SmartModeProvider
import com.intellij.openapi.project.SmartModeScheduler
import com.intellij.openapi.project.UnindexedFilesScannerExecutor
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.merge

/**
 * Reports the scanning of [UnindexedFilesScannerExecutor] and the dumb mode of [DumbService].
 */
internal class BackendSmartModeProvider : SmartModeProvider {
  override fun getCurrentMode(project: Project): Int {
    // The scanner unloads with this module, and a reader can still hold this provider at that time.
    val scanning = project.serviceOrNull<UnindexedFilesScannerExecutor>()?.isRunning?.value == true
    return (if (scanning) SmartModeScheduler.SCANNING else 0) +
           (if (DumbService.getInstance(project).state.value.isDumb) SmartModeScheduler.DUMB else 0)
  }

  override fun modeChanged(project: Project): Flow<*> =
    merge(DumbService.getInstance(project).state, UnindexedFilesScannerExecutor.getInstance(project).startedOrStoppedEvent)
}
