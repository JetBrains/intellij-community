// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.projectRoots.impl

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.readAction
import com.intellij.openapi.application.runReadActionBlocking
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.util.ui.update.DebouncedUpdates
import com.intellij.util.ui.update.UpdateQueue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.ApiStatus
import kotlin.time.Duration.Companion.milliseconds

@Service(Service.Level.PROJECT)
internal class UnknownSdkTrackerQueue(coroutineScope: CoroutineScope) : UnknownSdkCollectorQueue(mergingTimeSpaceMillis = 700, coroutineScope = coroutineScope) {
  companion object {
    fun getInstance(project: Project): UnknownSdkTrackerQueue = project.service<UnknownSdkTrackerQueue>()
  }
}

internal abstract class UnknownSdkCollectorQueue(mergingTimeSpaceMillis : Int, coroutineScope: CoroutineScope) {
  private val updateQueue: UpdateQueue<UnknownSdkTrackerTask> = DebouncedUpdates.forScope<UnknownSdkTrackerTask>(
    coroutineScope,
    javaClass.simpleName,
    mergingTimeSpaceMillis.milliseconds
  ).runLatest { task -> executeTask(task) }

  fun queue(task: UnknownSdkTrackerTask) {
    if (ApplicationManager.getApplication().isUnitTestMode) {
      val collector = task.createCollector() ?: return
      val result = runReadActionBlocking { collector.collectSdksUnderReadAction() }
      task.onLookupCompleted(result)
      return
    }

    updateQueue.queue(task)
  }

  private suspend fun executeTask(task: UnknownSdkTrackerTask) {
    val collector = task.createCollector() ?: return
    val result = readAction {
      collector.collectSdksUnderReadAction()
    }

    withContext(Dispatchers.EDT) {
      task.onLookupCompleted(result)
    }
  }
}

@ApiStatus.Internal
interface UnknownSdkTrackerTask {
  /**
   * Creates the collector or returns null of the task should be ignored
   */
  fun createCollector(): UnknownSdkCollector?

  /**
   * Executed only when the collector has completed and a given task is not merged with others.
   *
   * NOTE: this callback happened in EDT
   */
  fun onLookupCompleted(snapshot: UnknownSdkSnapshot)
}
