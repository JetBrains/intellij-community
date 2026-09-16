// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.diagnostic

import com.intellij.featureStatistics.fusCollectors.LifecycleUsageTriggerCollector
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.ExceptionWithAttachments
import com.intellij.openapi.diagnostic.IdeaLogRecord
import com.intellij.openapi.diagnostic.RuntimeExceptionWithAttachments
import com.intellij.openapi.diagnostic.UnhandledExceptionKind
import com.intellij.util.ExceptionUtil
import com.intellij.util.io.pagecache.impl.Throttler
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.job
import kotlinx.coroutines.joinAll
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

  @Suppress("RAW_SCOPE_CREATION") // DialogAppender is a process-wide root logger handler.
  private val coroutineScope = CoroutineScope(SupervisorJob() + DiagnosticDispatchers.Default + CoroutineName("DialogAppender"))

  private var earlyEventCounter = 0
  private val earlyEvents = ArrayDeque<Entry>()
  private var loggerBroken = AtomicBoolean(false)

  /** An error that goes to the message pool. [throwable] is the real cause. See IJPL-254578. */
  private class Entry(val message: String?, val throwable: Throwable, val unhandledExceptionKind: UnhandledExceptionKind)

  override fun publish(event: LogRecord) {
    if (event.level.intValue() < Level.SEVERE.intValue() || loggerBroken.get()) return

    val throwable = event.thrown ?: return
    // `JulLogger` drops the `UnhandledException` wrapper and puts the kind here. See IJPL-254578.
    val kind = (event as? IdeaLogRecord)?.unhandledExceptionKind ?: UnhandledExceptionKind.HANDLED
    val entry = Entry(event.message, throwable, kind)
    synchronized(this) {
      if (LoadingState.APP_READY.isOccurred) {
        processEarlyEventsIfNeeded()
        queueEvent(entry)
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
      queueEvent(entry)
    }

    if (earlyEventCounter > 0) {
      LifecycleUsageTriggerCollector.onEarlyErrorsIgnored(earlyEventCounter)
      earlyEventCounter = 0
    }
  }

  private fun queueEvent(entry: Entry) {
    coroutineScope.launch {
      processEvent(entry)
    }
  }

  private val oomReportsThrottler = Throttler(100, SECONDS)

  private fun processEvent(entry: Entry) {
    try {
      val app = ApplicationManager.getApplication()
      if (app == null || app.isExitInProgress || app.isDisposed()) return

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
        MessagePool.getInstance().addErrorMessage(
          LogMessage(throwable, message, attachments, entry.unhandledExceptionKind)
        )
      }
    }
    catch (e: Throwable) {
      loggerBroken.set(true)
      throw e
    }
  }

  @ApiStatus.Internal
  suspend fun awaitPendingJobs() {
    val currentPendingJobs = synchronized(this) {
      if (LoadingState.APP_READY.isOccurred) {
        processEarlyEventsIfNeeded()
      }
      coroutineScope.coroutineContext.job.children.toList()
    }
    currentPendingJobs.joinAll()
  }

  override fun flush() { }

  override fun close() { }
}
