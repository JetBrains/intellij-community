// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.debugger.collections.visualizer

import com.intellij.internal.statistic.eventLog.EventLogGroup
import com.intellij.internal.statistic.eventLog.events.EventFields
import com.intellij.internal.statistic.service.fus.collectors.CounterUsagesCollector
import com.intellij.openapi.project.Project

internal object CollectionVisualizerStatisticsCollector : CounterUsagesCollector() {

  private val GROUP = EventLogGroup("debugger.collection.visualizer", 1)
  private val closedEvent = GROUP.registerEvent("closed", EventFields.Enum("cause", VisualizerClosedCause::class.java))
  private val shownEvent = GROUP.registerEvent("shown")

  fun reportShown(project: Project) = shownEvent.log(project)
  fun reportClosed(project: Project, cause: VisualizerClosedCause) = closedEvent.log(project, cause)

  override fun getGroup(): EventLogGroup = GROUP
}

enum class VisualizerClosedCause {
  RESUME,
  DETACH_AUTO,
  DETACH_MANUAL,
}