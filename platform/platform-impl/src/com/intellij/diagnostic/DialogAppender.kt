// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.diagnostic

import com.intellij.featureStatistics.fusCollectors.LifecycleUsageTriggerCollector
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.ExceptionWithAttachments
import com.intellij.openapi.diagnostic.RuntimeExceptionWithAttachments
import com.intellij.util.ExceptionUtil
import com.intellij.util.io.pagecache.impl.Throttler
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.GlobalScope
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

  private val context = DiagnosticDispatchers.Default + CoroutineName("DialogAppender")

  private var earlyEventCounter = 0
  private val earlyEvents = ArrayDeque<Pair<String?, Throwable>>()
  private var loggerBroken = AtomicBoolean(false)

  override fun publish(event: LogRecord) {
    if (event.level.intValue() < Level.SEVERE.intValue() || loggerBroken.get()) return

    val throwable = event.thrown ?: return
    synchronized(this) {
      if (LoadingState.APP_READY.isOccurred) {
        processEarlyEventsIfNeeded()
        queueEvent(event.message, throwable)
      }
      else {
        earlyEventCounter++
        if (earlyEvents.size < MAX_EARLY_LOGGING_EVENTS) {
          earlyEvents.add(event.message to throwable)
        }
      }
    }
  }

  private fun processEarlyEventsIfNeeded() {
    if (earlyEventCounter == 0) return

    while (true) {
      val (message, throwable) = earlyEvents.poll() ?: break
      earlyEventCounter--
      queueEvent(message, throwable)
    }

    if (earlyEventCounter > 0) {
      LifecycleUsageTriggerCollector.onEarlyErrorsIgnored(earlyEventCounter)
      earlyEventCounter = 0
    }
  }

  @OptIn(DelicateCoroutinesApi::class)
  private fun queueEvent(message: String?, throwable: Throwable) {
    GlobalScope.launch(context) {
      processEvent(message, throwable)
    }
  }

  private val oomReportsThrottler = Throttler(100, SECONDS)

  private fun processEvent(message: String?, throwable: Throwable) {
    try {
      val app = ApplicationManager.getApplication()
      if (app == null || app.isExitInProgress || app.isDisposed()) return

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
        val message = withAttachments.asSequence().filterIsInstance<RuntimeExceptionWithAttachments>().firstOrNull()?.userMessage ?: message
        val attachments = withAttachments.asSequence().flatMap { it.attachments.asSequence() }.toList()
        // always add to MessagePool, dialog notification will decide if it shows or not in IdeMessagePanel
        MessagePool.getInstance().addErrorMessage(LogMessage(throwable, message, attachments))
      }
    }
    catch (e: Throwable) {
      loggerBroken.set(true)
      throw e
    }
  }

  override fun flush() { }

  override fun close() { }
}
