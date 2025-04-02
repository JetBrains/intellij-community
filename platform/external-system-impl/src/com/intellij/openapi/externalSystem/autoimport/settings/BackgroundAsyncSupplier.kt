// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.externalSystem.autoimport.settings

import com.intellij.openapi.Disposable
import com.intellij.openapi.externalSystem.util.ExternalSystemActivityKey
import com.intellij.openapi.progress.impl.CoreProgressManager
import com.intellij.openapi.progress.util.BackgroundTaskUtil
import com.intellij.openapi.project.Project
import com.intellij.platform.backend.observation.trackActivityBlocking
import com.intellij.util.application
import org.jetbrains.annotations.ApiStatus
import java.util.concurrent.Executor

@ApiStatus.Internal
class BackgroundAsyncSupplier<R>(
  private val supplier: AsyncSupplier<R>,
  private val shouldKeepTasksAsynchronous: () -> Boolean,
  private val backgroundExecutor: Executor,
) : AsyncSupplier<R> {
  companion object {
    fun isAsyncInHeadless(): Boolean {
      return CoreProgressManager.shouldKeepTasksAsynchronousInHeadlessMode() ||
             java.lang.Boolean.getBoolean("external.system.auto.import.headless.async")
    }
  }

  override fun supply(parentDisposable: Disposable, consumer: (R) -> Unit) {
    if (shouldKeepTasksAsynchronous()) {
      BackgroundTaskUtil.execute(backgroundExecutor, parentDisposable) {
        supplier.supply(parentDisposable, consumer)
      }
    }
    else {
      supplier.supply(parentDisposable, consumer)
    }
  }

  class Builder<R>(private val supplier: AsyncSupplier<R>) {

    constructor(supplier: () -> R) : this(AsyncSupplier.blocking(supplier))

    private var shouldKeepTasksAsynchronous: () -> Boolean = {
      val isHeadless = application.isUnitTestMode() || application.isHeadlessEnvironment()
      !isHeadless || isAsyncInHeadless()
    }

    fun shouldKeepTasksAsynchronous(provider: () -> Boolean) = apply {
      shouldKeepTasksAsynchronous = provider
    }

    fun build(backgroundExecutor: Executor): AsyncSupplier<R> {
      return BackgroundAsyncSupplier(supplier, shouldKeepTasksAsynchronous, backgroundExecutor)
    }
  }
}

@ApiStatus.Internal
fun <R> AsyncSupplier<R>.tracked(project: Project): AsyncSupplier<R> {
  return object : AsyncSupplier<R> {
    override fun supply(parentDisposable: Disposable, consumer: (R) -> Unit) {
      project.trackActivityBlocking(ExternalSystemActivityKey) {
        this@tracked.supply(parentDisposable, consumer)
      }
    }
  }
}