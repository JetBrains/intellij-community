// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.accessibility

import com.intellij.internal.statistic.eventLog.EventLogGroup
import com.intellij.internal.statistic.eventLog.events.EventId
import com.intellij.internal.statistic.eventLog.fus.FeatureUsageLogger
import com.intellij.internal.statistic.service.fus.collectors.CounterUsagesCollector
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity
import kotlinx.coroutines.future.await
import java.util.Queue
import java.util.concurrent.ConcurrentLinkedQueue

private val LOG = logger<AccessibilityUsageTrackerCollector>()

internal object AccessibilityUsageTrackerCollector : CounterUsagesCollector() {
  override fun getGroup(): EventLogGroup = GROUP

  class CollectStatisticsTask : ProjectActivity {
    override suspend fun execute(project: Project) {
      logRaisedEvents()
    }
  }

  private val raisedEvents: Queue<EventId> = ConcurrentLinkedQueue()
  private val GROUP = EventLogGroup("accessibility", 2)

  @JvmField
  val SCREEN_READER_DETECTED: EventId = GROUP.registerEvent("screen.reader.detected")
  @JvmField
  val SCREEN_READER_SUPPORT_ENABLED: EventId = GROUP.registerEvent("screen.reader.support.enabled")
  @JvmField
  val SCREEN_READER_SUPPORT_ENABLED_VM: EventId = GROUP.registerEvent("screen.reader.support.enabled.in.vmoptions")
  @JvmField
  val LINUX_ACCESSIBILITY_SUPPORT_ENABLED: EventId = GROUP.registerEvent("linux.accessibility.support.enabled")

  @JvmStatic
  fun featureTriggered(feature: EventId) {
    raisedEvents.add(feature)
  }

  suspend fun flushRaisedEvents() {
    if (!logRaisedEvents()) {
      return
    }

    runCatching {
      FeatureUsageLogger.getInstance().flush().await()
    }.onFailure {
      LOG.warn("Failed to flush accessibility usage events", it)
    }
  }

  private fun logRaisedEvents(): Boolean {
    var logged = false
    while (true) {
      val feature = raisedEvents.poll() ?: return logged
      feature.log()
      logged = true
    }
  }
}