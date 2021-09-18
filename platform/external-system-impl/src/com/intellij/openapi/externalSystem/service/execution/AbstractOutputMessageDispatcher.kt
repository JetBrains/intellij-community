// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.externalSystem.service.execution

import com.intellij.build.BuildProgressListener
import com.intellij.build.events.BuildEvent
import com.intellij.build.events.FinishBuildEvent
import com.intellij.openapi.diagnostic.logger
import com.intellij.util.containers.ContainerUtil
import org.jetbrains.annotations.ApiStatus
import java.util.concurrent.CompletableFuture
import java.util.function.Consumer

@ApiStatus.Experimental
abstract class AbstractOutputMessageDispatcher(private val buildProgressListener: BuildProgressListener) : ExternalSystemOutputMessageDispatcher {
  private val onCompletionHandlers = ContainerUtil.createConcurrentList<Consumer<in Throwable?>>()
  @Volatile
  private var isClosed: Boolean = false

  override fun onEvent(buildId: Any, event: BuildEvent) =
    when (event) {
      is FinishBuildEvent -> invokeOnCompletion(Consumer { buildProgressListener.onEvent(buildId, event) })
      else -> buildProgressListener.onEvent(buildId, event)
    }

  override fun invokeOnCompletion(handler: Consumer<in Throwable?>) {
    if (isClosed) {
      LOG.warn("Attempt to add completion handler for closed output dispatcher, the handler will be ignored",
               if (LOG.isDebugEnabled) Throwable() else null)
    }
    else {
      onCompletionHandlers.add(handler)
    }
  }

  protected abstract fun closeAndGetFuture(): CompletableFuture<*>

  final override fun close() {
    val future = closeAndGetFuture()
    isClosed = true
    for (handler in onCompletionHandlers.asReversed()) {
      future.whenComplete { _, u -> handler.accept(u) }
    }
    onCompletionHandlers.clear()
  }

  companion object {
    private val LOG = logger<AbstractOutputMessageDispatcher>()
  }
}