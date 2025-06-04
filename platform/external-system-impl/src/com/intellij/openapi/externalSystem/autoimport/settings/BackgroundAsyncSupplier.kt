// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.externalSystem.autoimport.settings

import com.intellij.openapi.Disposable
import com.intellij.openapi.externalSystem.util.ExternalSystemActivityKey
import com.intellij.openapi.progress.util.BackgroundTaskUtil
import com.intellij.openapi.project.Project
import com.intellij.platform.backend.observation.trackActivityBlocking
import org.jetbrains.annotations.ApiStatus
import java.util.concurrent.Executor

@ApiStatus.Internal
class BackgroundAsyncSupplier<R>(
  private val project: Project,
  private val supplier: AsyncSupplier<R>,
  private val shouldKeepTasksAsynchronous: () -> Boolean,
  private val backgroundExecutor: Executor,
) : AsyncSupplier<R> {
  override fun supply(parentDisposable: Disposable, consumer: (R) -> Unit) {
    project.trackActivityBlocking(ExternalSystemActivityKey) {
      if (shouldKeepTasksAsynchronous()) {
        BackgroundTaskUtil.execute(backgroundExecutor, parentDisposable) {
          supplier.supply(parentDisposable, consumer)
        }
      }
      else {
        supplier.supply(parentDisposable, consumer)
      }
    }
  }
}