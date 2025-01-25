// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.diagnostic

import com.intellij.featureStatistics.fusCollectors.LifecycleUsageTriggerCollector
import com.intellij.ide.plugins.PluginManagerCore
import com.intellij.ide.plugins.PluginUtil
import com.intellij.idea.AppMode
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ex.ApplicationManagerEx
import com.intellij.openapi.diagnostic.ExceptionWithAttachments
import com.intellij.openapi.diagnostic.IdeaLoggingEvent
import com.intellij.openapi.diagnostic.RuntimeExceptionWithAttachments
import com.intellij.openapi.updateSettings.impl.UpdateChecker
import com.intellij.openapi.updateSettings.impl.UpdateSettings
import com.intellij.util.ExceptionUtil
import kotlinx.coroutines.*
import org.jetbrains.annotations.ApiStatus
import java.util.*
import java.util.concurrent.atomic.AtomicBoolean
import java.util.logging.Handler
import java.util.logging.Level
import java.util.logging.LogRecord

@ApiStatus.Internal
class DialogAppender : Handler() {
  private val MAX_EARLY_LOGGING_EVENTS = 20
  private val FATAL_ERROR_NOTIFICATION_PROPERTY = "idea.fatal.error.notification"
  private val DISABLED_VALUE = "disabled"

  @OptIn(ExperimentalCoroutinesApi::class)
  private val context = Dispatchers.IO.limitedParallelism(1) + CoroutineName("DialogAppender")

  private var earlyEventCounter = 0
  private val earlyEvents = ArrayDeque<Pair<String?, Throwable>>()
  private var loggerBroken = AtomicBoolean(false)
  private val pluginUpdateScheduled = AtomicBoolean(false)

  override fun publish(event: LogRecord) {
    if (
      event.level.intValue() < Level.SEVERE.intValue() ||
      loggerBroken.get() ||
      (AppMode.isCommandLine() && !ApplicationManagerEx.isInIntegrationTest())
    ) return

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

  private fun processEvent(message: String?, throwable: Throwable) {
    try {
      val app = ApplicationManager.getApplication()
      if (app == null || app.isExitInProgress || app.isDisposed()) return

      val oomErrorKind = DefaultIdeaErrorLogger.getOOMErrorKind(throwable)
      if (oomErrorKind != null) {
        LowMemoryNotifier.showNotification(oomErrorKind, true)
      }
      else {
        val notificationEnabled = System.getProperty(FATAL_ERROR_NOTIFICATION_PROPERTY) != DISABLED_VALUE

        val plugin = PluginManagerCore.getPlugin(PluginUtil.getInstance().findPluginId(throwable))
        val submitter = DefaultIdeaErrorLogger.findSubmitter(throwable, plugin)
        val showPluginError = submitter !is ITNReporter || submitter.showErrorInRelease(IdeaLoggingEvent(message, throwable))

        if (plugin != null && !plugin.isBundled && !pluginUpdateScheduled.getAndSet(true) && UpdateSettings.getInstance().isPluginsCheckNeeded) {
          UpdateChecker.updateAndShowResult()
        }

        if (app.isInternal() || notificationEnabled || showPluginError) {
          val withAttachments = ExceptionUtil.causeAndSuppressed(throwable, ExceptionWithAttachments::class.java).toList()
          val message = withAttachments.asSequence().filterIsInstance<RuntimeExceptionWithAttachments>().firstOrNull()?.userMessage ?: message
          val attachments = withAttachments.asSequence().flatMap { it.attachments.asSequence() }.toList()
          MessagePool.getInstance().addIdeFatalMessage(LogMessage(throwable, message, attachments))
        }
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
