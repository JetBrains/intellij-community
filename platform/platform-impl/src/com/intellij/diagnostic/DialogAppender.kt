// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.diagnostic

import com.intellij.featureStatistics.fusCollectors.LifecycleUsageTriggerCollector
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.ExceptionWithAttachments
import com.intellij.openapi.diagnostic.IdeaLogRecord
import com.intellij.openapi.diagnostic.RuntimeExceptionWithAttachments
import com.intellij.openapi.diagnostic.UnhandledExceptionKind
import com.intellij.openapi.util.ShutDownTracker
import com.intellij.util.ExceptionUtil
import com.intellij.util.io.pagecache.impl.Throttler
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import org.jetbrains.annotations.ApiStatus
import java.util.ArrayDeque
import java.util.concurrent.TimeUnit.SECONDS
import java.util.concurrent.atomic.AtomicBoolean
import java.util.logging.Handler
import java.util.logging.Level
import java.util.logging.LogRecord

@ApiStatus.Internal
class DialogAppender : Handler() {
  private val MAX_EARLY_LOGGING_EVENTS = 20

  private var earlyEventCounter = 0
  private val earlyEvents = ArrayDeque<Entry>()
  private var loggerBroken = AtomicBoolean(false)
  private val queue = Channel<Message>(Channel.UNLIMITED)

  private sealed interface Message
  /** An error that goes to the message pool. [throwable] is the real cause. See IJPL-254578. */
  private class Entry(val message: String?, val throwable: Throwable, val unhandledExceptionKind: UnhandledExceptionKind) : Message
  private class Barrier(val future: CompletableDeferred<Unit>) : Message

  init {
    @Suppress("RAW_SCOPE_CREATION") // DialogAppender is a process-wide root logger handler.
    CoroutineScope(SupervisorJob() + CoroutineName("DialogAppender")).launch {
      for (item in queue) {
        when (item) {
          is Entry -> processEvent(item)
          is Barrier -> item.future.complete(Unit)
        }
      }
    }
  }

  override fun publish(event: LogRecord) {
    if (event.level.intValue() < Level.SEVERE.intValue() || loggerBroken.get()) return

    val throwable = event.thrown ?: return
    // Check here, not in processEvent(): the queue runs later, possibly after the analysis ends.
    if (DefaultIdeaErrorLogger.getOOMErrorKind(throwable) == VMOptions.MemoryKind.HEAP && LowMemoryNotifier.isMemoryAnalysisRunning()) return
    // `JulLogger` drops the `UnhandledException` wrapper and puts the kind here. See IJPL-254578.
    val kind = (event as? IdeaLogRecord)?.unhandledExceptionKind ?: UnhandledExceptionKind.HANDLED
    val entry = Entry(event.message, throwable, kind)
    synchronized(this) {
      if (LoadingState.APP_READY.isOccurred) {
        processEarlyEventsIfNeeded()
        queue.trySend(entry)
      }
      else {
        earlyEventCounter++
        if (earlyEvents.size < MAX_EARLY_LOGGING_EVENTS) {
          earlyEvents.add(entry)
        }
      }
    }
  }

  private fun processEarlyEventsIfNeeded() {
    if (earlyEventCounter == 0) return

    while (true) {
      val entry = earlyEvents.poll() ?: break
      earlyEventCounter--
      queue.trySend(entry)
    }

    if (earlyEventCounter > 0) {
      LifecycleUsageTriggerCollector.onEarlyErrorsIgnored(earlyEventCounter)
      earlyEventCounter = 0
    }
  }

  private val oomReportsThrottler = Throttler(100, SECONDS)

  private suspend fun processEvent(entry: Entry) {
    try {
      val app = ApplicationManager.getApplication()
      if (app == null || app.isExitInProgress || app.isDisposed() || ShutDownTracker.isShutdownStarted()) return

      val throwable = entry.throwable
      val oomErrorKind = DefaultIdeaErrorLogger.getOOMErrorKind(throwable)
      if (oomErrorKind != null) {
        val shouldNotify = synchronized(oomReportsThrottler) {
          oomReportsThrottler.isTimeForNextRun(System.nanoTime())
        }
        if (shouldNotify) {
          LowMemoryNotifier.showNotification(oomErrorKind, /*oomError: */true)
        }
      }
      else {
        val withAttachments = ExceptionUtil.causeAndSuppressed(throwable, ExceptionWithAttachments::class.java).toList()
        val message = withAttachments.asSequence().filterIsInstance<RuntimeExceptionWithAttachments>().firstOrNull()?.userMessage
                      ?: entry.message
        val attachments = withAttachments.asSequence().flatMap { it.attachments.asSequence() }.toList()
        // always add to MessagePool, dialog notification will decide if it shows or not in IdeMessagePanel
        MessagePool.getInstance().addErrorMessage(LogMessage(throwable, message, attachments, entry.unhandledExceptionKind))
      }
    }
    catch (e: Throwable) {
      loggerBroken.set(true)
      throw e
    }
  }

  @ApiStatus.Internal
  suspend fun awaitPendingJobs() {
    synchronized(this) {
      processEarlyEventsIfNeeded()
    }

    val future = CompletableDeferred<Unit>()
    queue.send(Barrier(future))
    future.await()
  }

  override fun flush() {}

  override fun close() {}
}
