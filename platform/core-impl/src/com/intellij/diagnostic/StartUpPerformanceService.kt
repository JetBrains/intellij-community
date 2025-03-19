// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.diagnostic

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import it.unimi.dsi.fastutil.objects.Object2IntMap
import it.unimi.dsi.fastutil.objects.Object2LongMap
import kotlinx.coroutines.flow.Flow
import org.jetbrains.annotations.ApiStatus
import java.nio.ByteBuffer

@ApiStatus.Internal
interface StartUpPerformanceService {
  companion object {
    fun getInstance(): StartUpPerformanceService = ApplicationManager.getApplication().service<StartUpPerformanceService>()
  }

  // async execution
  fun reportStatistics(project: Project)

  fun getPluginCostMap(): Map<String, Object2LongMap<String>>

  fun getMetrics(): Flow<Object2IntMap<String>>

  fun getLastReport(): ByteBuffer?

  fun projectDumbAwareActivitiesFinished() { }

  fun editorRestoringTillHighlighted() { }
}
