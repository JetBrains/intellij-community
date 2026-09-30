// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.externalSystem.service.execution

import com.intellij.build.BuildProgressListener
import com.intellij.build.BuildViewSettingsProvider
import com.intellij.build.events.BuildEvent
import com.intellij.build.events.BuildId
import com.intellij.build.events.FinishBuildEvent
import com.intellij.build.events.StartId
import com.intellij.build.output.BuildOutputInstantReaderImpl.Companion.asLineParser
import com.intellij.build.output.BuildOutputMulticaster
import com.intellij.build.output.BuildOutputParser
import com.intellij.build.output.BuildOutputParserDispatcher
import com.intellij.build.output.BuildOutputParserDispatcherImpl
import com.intellij.diagnostic.rethrowControlFlowException
import com.intellij.openapi.diagnostic.logger
import com.intellij.util.containers.ContainerUtil
import org.jetbrains.annotations.ApiStatus.Internal
import org.jetbrains.annotations.ApiStatus.OverrideOnly
import java.util.function.Consumer

@Internal
@OverrideOnly
abstract class AbstractExternalSystemOutputMessageDispatcher(
  protected val buildId: BuildId,
  private val listener: BuildProgressListener,
  parsers: List<BuildOutputParser>,
  protected val consoleSettings: BuildViewSettingsProvider,
) : ExternalSystemOutputMessageDispatcher {

  @Volatile
  private var isClosed: Boolean = false

  private val onCompletionHandlers = ContainerUtil.createConcurrentList<Consumer<in Throwable?>>()

  private val multicaster = object : BuildOutputMulticaster {
    override fun notifyBuildEvent(event: BuildEvent) {
      notifyBuildEvent(buildId, event)
    }
  }

  private val lineParsers = parsers.map { it.asLineParser(consoleSettings) }

  protected fun createParserDispatcher(parentEventId: StartId): BuildOutputParserDispatcher {
    return BuildOutputParserDispatcherImpl(parentEventId, lineParsers, multicaster)
  }

  override fun notifyBuildEvent(buildId: BuildId, event: BuildEvent) {
    when (event) {
      is FinishBuildEvent -> invokeOnCompletion(Consumer { listener.onEvent(buildId, event) })
      else -> listener.onEvent(buildId, event)
    }
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

  abstract fun doClose()

  final override fun close() {
    val exception = runCatching { doClose() }.exceptionOrNull()

    isClosed = true

    for (handler in onCompletionHandlers) {
      try {
        handler.accept(exception)
      }
      catch (exception: Exception) {
        //TODO: Closeable.close() should not (re)throw cancellation exceptions, 'cos it may interact badly with
        //      try-with-resource pattern: see IJPL-248654 for details
        rethrowControlFlowException(exception)
        LOG.warn(exception)
      }
    }
  }

  companion object {
    private val LOG = logger<AbstractExternalSystemOutputMessageDispatcher>()
  }
}