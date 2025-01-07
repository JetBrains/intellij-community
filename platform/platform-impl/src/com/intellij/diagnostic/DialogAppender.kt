// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.diagnostic

import com.intellij.featureStatistics.fusCollectors.LifecycleUsageTriggerCollector
import com.intellij.idea.AppMode
import com.intellij.openapi.application.ex.ApplicationManagerEx
import com.intellij.openapi.diagnostic.Attachment
import com.intellij.openapi.diagnostic.ExceptionWithAttachments
import com.intellij.openapi.diagnostic.IdeaLoggingEvent
import com.intellij.openapi.diagnostic.RuntimeExceptionWithAttachments
import com.intellij.util.ExceptionUtil
import kotlinx.coroutines.*
import org.jetbrains.annotations.ApiStatus
import java.util.*
import java.util.logging.Handler
import java.util.logging.Level
import java.util.logging.LogRecord

@ApiStatus.Internal
class DialogAppender : Handler() {
  private val MAX_EARLY_LOGGING_EVENTS = 20

  private var earlyEventCounter = 0
  private val earlyEvents = ArrayDeque<IdeaLoggingEvent>()
  @OptIn(ExperimentalCoroutinesApi::class)
  private val context = Dispatchers.IO.limitedParallelism(1) + CoroutineName("DialogAppender")

  override fun publish(event: LogRecord) {
    if (event.level.intValue() < Level.SEVERE.intValue() || (AppMode.isCommandLine() && !ApplicationManagerEx.isInIntegrationTest())) {
      // the dialog appender doesn't deal with non-critical errors
      // also, it makes no sense when there is no frame to show an error icon
      return
    }

    val ideaEvent: IdeaLoggingEvent
    val parameters = event.parameters
    if (parameters?.firstOrNull() is IdeaLoggingEvent) {
      ideaEvent = parameters[0] as IdeaLoggingEvent
    }
    else {
      val thrown = event.thrown ?: return
      ideaEvent = extractLoggingEvent(messageObject = event.message, throwable = thrown)
    }

    synchronized(this) {
      if (LoadingState.APP_READY.isOccurred) {
        processEarlyEventsIfNeeded()
        queueAppend(ideaEvent)
      }
      else {
        earlyEventCounter++
        if (earlyEvents.size < MAX_EARLY_LOGGING_EVENTS) {
          earlyEvents.add(ideaEvent)
        }
      }
    }
  }

  private fun processEarlyEventsIfNeeded() {
    if (earlyEventCounter == 0) return

    while (true) {
      val queued = earlyEvents.poll() ?: break
      earlyEventCounter--
      queueAppend(queued)
    }

    if (earlyEventCounter > 0) {
      LifecycleUsageTriggerCollector.onEarlyErrorsIgnored(earlyEventCounter)
      earlyEventCounter = 0
    }
  }

  @OptIn(DelicateCoroutinesApi::class)
  private fun queueAppend(event: IdeaLoggingEvent) {
    if (DefaultIdeaErrorLogger.canHandle(event)) {
      GlobalScope.launch(context) {
        DefaultIdeaErrorLogger.handle(event)
      }
    }
  }

  override fun flush() { }

  override fun close() { }

  private fun extractLoggingEvent(messageObject: Any?, throwable: Throwable): IdeaLoggingEvent {
    var message: String? = null
    val withAttachments = ExceptionUtil.causeAndSuppressed(throwable, ExceptionWithAttachments::class.java).toList()
    (withAttachments.firstOrNull() as? RuntimeExceptionWithAttachments)?.let {
      message = it.userMessage
    }
    if (message == null && messageObject != null) {
      message = messageObject.toString()
    }

    if (withAttachments.isEmpty()) {
      return IdeaLoggingEvent(message, throwable)
    }
    else {
      val list = ArrayList<Attachment>()
      for (e in withAttachments) {
        list.addAll(e.attachments)
      }
      return LogMessage.eventOf(throwable, message, list)
    }
  }
}
