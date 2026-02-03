// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.todo

import com.intellij.internal.statistic.beans.MetricEvent
import com.intellij.internal.statistic.eventLog.EventLogGroup
import com.intellij.internal.statistic.eventLog.events.EventFields
import com.intellij.internal.statistic.service.fus.collectors.ApplicationUsagesCollector
import com.intellij.openapi.components.serviceAsync

internal class TodoConfigurationCollector: ApplicationUsagesCollector() {
  override suspend fun getMetricsAsync(): Set<MetricEvent> {
    val todoConfiguration = serviceAsync<TodoConfiguration>()
    return setOf(
      TODO_PATTERNS.metric(todoConfiguration.todoPatterns.size),
      NON_DEFAULT_TODO_PATTERNS.metric((todoConfiguration.todoPatterns.toHashSet() - todoConfiguration.defaultPatterns.toHashSet()).size),
      TODO_FILTERS.metric(todoConfiguration.todoFilters.size)
    )
  }

  override fun getGroup(): EventLogGroup = GROUP

  private val GROUP = EventLogGroup("todo.configuration", 2)
  private val TODO_PATTERNS = GROUP.registerEvent("todo.patterns", EventFields.Count)
  private val NON_DEFAULT_TODO_PATTERNS = GROUP.registerEvent("non.default.todo.patterns", EventFields.Count)
  private val TODO_FILTERS = GROUP.registerEvent("todo.filters", EventFields.Count)
}