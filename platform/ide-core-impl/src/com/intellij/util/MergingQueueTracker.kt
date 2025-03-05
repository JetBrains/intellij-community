// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.serviceAsync
import com.intellij.openapi.project.Project
import com.intellij.platform.backend.observation.ActivityTracker
import com.intellij.util.ui.update.MergingUpdateQueueTracker
import kotlinx.coroutines.delay
import org.jetbrains.annotations.ApiStatus
import java.util.concurrent.atomic.AtomicInteger
import kotlin.time.Duration.Companion.milliseconds

@ApiStatus.Internal
class MergingUpdateQueueActivityTracker : ActivityTracker {
  override val presentableName: String
    get() = "Queries for MergingUpdateQueue"

  override suspend fun isInProgress(project: Project): Boolean {
    return (ApplicationManager.getApplication().serviceAsync<MergingUpdateQueueTracker>() as MergingUpdateQueueTrackerImpl).counter.get() > 0 // if true, someone queued a task
  }

  override suspend fun awaitConfiguration(project: Project) {
    while (isInProgress(project)) {
      delay(100.milliseconds)
    }
  }
}

private class MergingUpdateQueueTrackerImpl : MergingUpdateQueueTracker {
  @JvmField val counter = AtomicInteger(0)

  override fun registerEnter() {
    counter.incrementAndGet()
  }

  override fun registerExit() {
    counter.decrementAndGet()
  }
}