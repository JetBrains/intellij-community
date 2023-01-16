// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.impl.ApplicationImpl
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.progress.impl.pumpEventsUntilJobIsCompleted
import com.intellij.openapi.util.registry.Registry
import com.intellij.util.ui.EDT
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.job
import org.jetbrains.annotations.ApiStatus.Internal

private val LOG = Logger.getInstance("#com.intellij.ide.shutdown")

// todo convert ApplicationImpl and IdeEventQueue to kotlin

internal fun ApplicationImpl.joinBlocking() {
  EDT.assertIsEdt()
  LOG.assertTrue(!ApplicationManager.getApplication().isWriteAccessAllowed)
  if (!Registry.`is`("ide.await.scope.completion")) {
    return
  }
  val containerJob = coroutineScope.coroutineContext.job
  if (!containerJob.isCancelled) {
    LOG.error("Application container scope is expected to be cancelled during disposal")
    containerJob.cancel()
  }
  if (containerJob.isCompleted) {
    LOG.trace("$this: application scope is already completed")
    return
  }
  LOG.trace("$this: waiting for application scope completion")
  IdeEventQueue.getInstance().pumpEventsUntilJobIsCompleted(containerJob)
  LOG.trace("$this: application scope was completed")
}

@Internal
internal fun <T> removeListenerOnCompletion(coroutineScope: CoroutineScope, listener: T, listeners: MutableList<T>) {
  coroutineScope.coroutineContext.job.invokeOnCompletion {
    listeners.remove(listener)
  }
}
