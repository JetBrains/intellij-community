// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.execution.dashboard.backend

import com.intellij.execution.RunManagerListener
import com.intellij.execution.RunnerAndConfigurationSettings
import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

private class RunDashboardConfigurationsListener(project: Project) : RunManagerListener {
  private var updateInProgress = false
  private val updatesHolder = RunDashboardConfigurationUpdatesHolder.getInstance(project)

  override fun runConfigurationAdded(settings: RunnerAndConfigurationSettings) {
    if (!updateInProgress) {
      updatesHolder.fireUpdate()
    }
  }

  override fun runConfigurationRemoved(settings: RunnerAndConfigurationSettings) {
    if (!updateInProgress) {
      updatesHolder.fireUpdate()
    }
  }

  override fun beginUpdate() {
    updateInProgress = true
  }

  override fun endUpdate() {
    updateInProgress = false
    updatesHolder.fireUpdate()
  }
}

@Service(Service.Level.PROJECT)
internal class RunDashboardConfigurationUpdatesHolder {
  companion object {
    fun getInstance(project: Project): RunDashboardConfigurationUpdatesHolder {
      return project.getService(RunDashboardConfigurationUpdatesHolder::class.java)
    }
  }

  private val updates = MutableSharedFlow<Unit>(replay = 1, extraBufferCapacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)

  fun fireUpdate() {
    updates.tryEmit(Unit)
  }

  fun getUpdates(): SharedFlow<Unit> {
    return updates.asSharedFlow()
  }
}