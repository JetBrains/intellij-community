// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.projectRoots.impl

import com.intellij.openapi.Disposable
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.util.ui.update.MergingUpdateQueue
import com.intellij.util.ui.update.Update

@Service // project
internal class UnknownSdkTrackerQueue : UnknownSdkCollectorQueue(700) {
  companion object {
    @JvmStatic
    fun getInstance(project: Project) = project.service<UnknownSdkTrackerQueue>()
  }
}

internal abstract class UnknownSdkCollectorQueue(mergingTimeSpaceMillis : Int) : Disposable {
  private val myUpdateQueue = MergingUpdateQueue(javaClass.simpleName,
                                                 mergingTimeSpaceMillis,
                                                 true,
                                                 null,
                                                 this,
                                                 null,
                                                 false).usePassThroughInUnitTestMode()

  override fun dispose() = Unit

  fun queue(task: UnknownSdkTrackerTask) {
    myUpdateQueue.queue(object : Update(this) {
      override fun run() {
        val collector = task.createCollector() ?: return
        collector.run { collectSdksPromise { task.onLookupCompleted(it) } }
      }
    })
  }
}

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
