// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.diagnostic

import com.intellij.idea.AppMode
import com.intellij.openapi.diagnostic.Attachment
import com.intellij.openapi.diagnostic.ExceptionWithAttachments
import com.intellij.openapi.diagnostic.IdeaLoggingEvent
import com.intellij.openapi.diagnostic.RuntimeExceptionWithAttachments
import com.intellij.util.ExceptionUtil
import com.intellij.util.concurrency.AppExecutorUtil
import org.jetbrains.annotations.ApiStatus
import java.util.*
import java.util.logging.Handler
import java.util.logging.Level
import java.util.logging.LogRecord
import kotlin.concurrent.Volatile

@ApiStatus.Internal
class DialogAppender : Handler() {
  companion object {
    //TODO android update checker accesses project jdk, fix it and remove
    fun delayPublishingForcibly() {
      delay = true
    }

    fun stopForceDelaying() {
      delay = false
    }
  }

  private var earlyEventCounter = 0
  private val earlyEvents = ArrayDeque<IdeaLoggingEvent>()
  private val executor = AppExecutorUtil.createBoundedScheduledExecutorService("DialogAppender", 1)

  override fun publish(event: LogRecord) {
    if (event.level.intValue() < Level.SEVERE.intValue() || AppMode.isCommandLine()) {
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
      if (LoadingState.COMPONENTS_LOADED.isOccurred && !delay) {
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
    var queued: IdeaLoggingEvent
    while ((earlyEvents.poll().also { queued = it }) != null) {
      earlyEventCounter--
      queueAppend(queued)
    }
    if (earlyEventCounter > 0) {
      queueAppend(IdeaLoggingEvent(DiagnosticBundle.message("error.monitor.early.errors.skipped", earlyEventCounter), Throwable()))
    }
  }

  private fun queueAppend(event: IdeaLoggingEvent) {
    if (DefaultIdeaErrorLogger.canHandle(event)) {
      executor.execute { DefaultIdeaErrorLogger.handle(event) }
    }
  }

  override fun flush() {}

  override fun close() {}
}

@Volatile
private var delay = false

private const val MAX_EARLY_LOGGING_EVENTS = 5

private fun extractLoggingEvent(messageObject: Any?, throwable: Throwable): IdeaLoggingEvent {
  var message: String? = null
  val withAttachments = ExceptionUtil.findCauseAndSuppressed(throwable, ExceptionWithAttachments::class.java)
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
