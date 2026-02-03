// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.diagnostic

import com.intellij.featureStatistics.fusCollectors.LifecycleUsageTriggerCollector
import com.intellij.ide.plugins.IdeaPluginDescriptor
import com.intellij.ide.plugins.PluginManagerCore
import com.intellij.ide.plugins.PluginUtil
import com.intellij.idea.AppMode
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ex.ApplicationManagerEx
import com.intellij.openapi.diagnostic.ExceptionWithAttachments
import com.intellij.openapi.diagnostic.IdeaLoggingEvent
import com.intellij.openapi.diagnostic.RuntimeExceptionWithAttachments
import com.intellij.openapi.updateSettings.impl.UpdateCheckerFacade
import com.intellij.openapi.updateSettings.impl.UpdateSettings
import com.intellij.util.ExceptionUtil
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import org.jetbrains.annotations.ApiStatus
import java.util.ArrayDeque
import java.util.concurrent.atomic.AtomicBoolean
import java.util.logging.Handler
import java.util.logging.Level
import java.util.logging.LogRecord

@ApiStatus.Internal
class DialogAppender : Handler() {
  private val MAX_EARLY_LOGGING_EVENTS = 20
  private val NOTIFICATIONS_ENABLED = System.getProperty("idea.fatal.error.notification") != "disabled"

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
        val plugin = findPlugin(throwable)
        if (app.isInternal() || NOTIFICATIONS_ENABLED || showPluginError(throwable, message, plugin)) {
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

  private fun findPlugin(throwable: Throwable): IdeaPluginDescriptor? {
    val plugin = PluginManagerCore.getPlugin(PluginUtil.getInstance().findPluginId(throwable))
    if (plugin != null && !plugin.isBundled && !pluginUpdateScheduled.getAndSet(true) && UpdateSettings.getInstance().isPluginsCheckNeeded) {
      UpdateCheckerFacade.getInstance().updateAndShowResult()
    }
    return plugin
  }

  private fun showPluginError(throwable: Throwable, message: String?, plugin: IdeaPluginDescriptor?): Boolean {
    val submitter = DefaultIdeaErrorLogger.findSubmitter(throwable, plugin)
    return submitter !is ITNReporter || submitter.showErrorInRelease(IdeaLoggingEvent(message, throwable))
  }

  override fun flush() { }

  override fun close() { }
}
