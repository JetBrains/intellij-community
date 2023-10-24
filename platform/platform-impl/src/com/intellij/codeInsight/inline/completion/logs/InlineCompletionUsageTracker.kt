// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.inline.completion.logs

import com.intellij.codeInsight.inline.completion.InlineCompletionEventAdapter
import com.intellij.codeInsight.inline.completion.InlineCompletionEventType
import com.intellij.codeInsight.inline.completion.InlineCompletionRequest
import com.intellij.internal.statistic.eventLog.EventLogGroup
import com.intellij.internal.statistic.service.fus.collectors.CounterUsagesCollector
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.util.application
import com.intellij.util.containers.ContainerUtil
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock
import kotlin.coroutines.cancellation.CancellationException

object InlineCompletionUsageTracker : CounterUsagesCollector() {
  internal val GROUP = EventLogGroup("inline.completion", 12)

  override fun getGroup() = GROUP

  private val requestIds = ContainerUtil.createConcurrentWeakMap<InlineCompletionRequest, Long>()

  fun getRequestId(request: InlineCompletionRequest): Long = requestIds[request] ?: -1

  class Listener : InlineCompletionEventAdapter {
    private val lock = ReentrantLock()
    private var invocationTracker: InlineCompletionInvocationTracker? = null
    private var showTracker: InlineCompletionShowTracker? = null

    override fun onRequest(event: InlineCompletionEventType.Request) = lock.withLock {
      invocationTracker = InlineCompletionInvocationTracker(event).also {
        requestIds[event.request] = it.requestId
        application.runReadAction { it.captureContext(event.request.editor, event.request.endOffset) }
      }
    }

    override fun onShow(event: InlineCompletionEventType.Show) = lock.withLock {
      if (event.i == 0 && !event.element.text.isEmpty()) {
        invocationTracker?.hasSuggestion()
      }
      if (event.i == 0) {
        // invocation tracker -> show tracker
        showTracker = invocationTracker?.createShowTracker()
        showTracker!!.firstShown(event.element)
      }
      if (event.i != 0) {
        showTracker!!.nextShown(event.element)
      }
    }

    override fun onChange(event: InlineCompletionEventType.Change) {
      showTracker!!.truncateTyping(event.overtypedLength)
    }

    override fun onInsert(event: InlineCompletionEventType.Insert): Unit = lock.withLock {
      showTracker?.selected()
    }

    override fun onHide(event: InlineCompletionEventType.Hide): Unit = lock.withLock {
      showTracker?.canceled(event.finishType)
    }

    override fun onEmpty(event: InlineCompletionEventType.Empty): Unit = lock.withLock {
      invocationTracker?.noSuggestions()
    }

    override fun onCompletion(event: InlineCompletionEventType.Completion): Unit = lock.withLock {
      if (!event.isActive || event.cause is CancellationException || event.cause is ProcessCanceledException) {
        invocationTracker?.canceled()
      }
      else if (event.cause != null) {
        invocationTracker?.exception()
      }
      invocationTracker?.finished()
      invocationTracker = null
    }
  }
}
