// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.projectRoots.impl

import com.intellij.openapi.Disposable
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.util.ui.update.MergingUpdateQueue
import com.intellij.util.ui.update.Update

@Service // project
class UnknownSdkTrackerQueue : Disposable {
  companion object {
    @JvmStatic
    fun getInstance(project: Project) = project.service<UnknownSdkTrackerQueue>()
  }

  private val myUpdateQueue = MergingUpdateQueue(javaClass.simpleName,
                                                 6700,
                                                 true,
                                                 null,
                                                 this,
                                                 null,
                                                 false).usePassThroughInUnitTestMode()

  override fun dispose() = Unit

  fun queue(task: Runnable) {
    myUpdateQueue.queue(object : Update(this) {
      override fun run() {
        task.run()
      }
    })
  }
}


