// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.projectRoots.impl

import com.intellij.openapi.Disposable
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.util.ui.update.MergingUpdateQueue
import com.intellij.util.ui.update.Update
import kotlinx.coroutines.CoroutineScope
import org.jetbrains.annotations.ApiStatus

@Service(Service.Level.PROJECT)
internal class UnknownSdkTrackerQueue(coroutineScope: CoroutineScope) : UnknownSdkCollectorQueue(mergingTimeSpaceMillis = 700, coroutineScope = coroutineScope) {
  companion object {
    fun getInstance(project: Project): UnknownSdkTrackerQueue = project.service<UnknownSdkTrackerQueue>()
  }
}

internal abstract class UnknownSdkCollectorQueue(mergingTimeSpaceMillis : Int, coroutineScope: CoroutineScope) : Disposable {
  private val myUpdateQueue = MergingUpdateQueue.mergingUpdateQueue(
    name = javaClass.simpleName,
    mergingTimeSpan = mergingTimeSpaceMillis,
    coroutineScope = coroutineScope,
  ).usePassThroughInUnitTestMode()

  override fun dispose(): Unit = Unit

  fun queue(task: UnknownSdkTrackerTask) {
    myUpdateQueue.queue(object : Update(this) {
      override fun run() {
        val collector = task.createCollector() ?: return
        collector.run { collectSdksPromise(this@UnknownSdkCollectorQueue) { task.onLookupCompleted(it) } }
      }
    })
  }
}

@ApiStatus.Internal
interface UnknownSdkTrackerTask {
  /**
   * Creates the collector or returns null of the task should be ignored
   */
  fun createCollector(): UnknownSdkCollector?

  /**
   * Executed only when collector has completed and a given
   * task is not merged with others.
   *
   * NOTE: this callback happened in EDT
   */
  fun onLookupCompleted(snapshot: UnknownSdkSnapshot)
}
