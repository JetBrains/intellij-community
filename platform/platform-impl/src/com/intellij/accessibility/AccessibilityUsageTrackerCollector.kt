// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.accessibility

import com.intellij.internal.statistic.eventLog.EventLogGroup
import com.intellij.internal.statistic.eventLog.events.EventId
import com.intellij.internal.statistic.service.fus.collectors.CounterUsagesCollector
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity
import java.util.concurrent.ConcurrentLinkedQueue

internal object AccessibilityUsageTrackerCollector : CounterUsagesCollector() {
  override fun getGroup(): EventLogGroup = GROUP

  class CollectStatisticsTask : ProjectActivity {
    override suspend fun execute(project: Project) {
      raisedEvents.forEach(EventId::log)
    }
  }

  private val raisedEvents: MutableCollection<EventId> = ConcurrentLinkedQueue()
  private val GROUP = EventLogGroup("accessibility", 1)

  @JvmField
  val SCREEN_READER_DETECTED: EventId = GROUP.registerEvent("screen.reader.detected")
  @JvmField
  val SCREEN_READER_SUPPORT_ENABLED: EventId = GROUP.registerEvent("screen.reader.support.enabled")
  @JvmField
  val SCREEN_READER_SUPPORT_ENABLED_VM: EventId = GROUP.registerEvent("screen.reader.support.enabled.in.vmoptions")

  @JvmStatic
  fun featureTriggered(feature: EventId) {
    raisedEvents.add(feature)
  }
}