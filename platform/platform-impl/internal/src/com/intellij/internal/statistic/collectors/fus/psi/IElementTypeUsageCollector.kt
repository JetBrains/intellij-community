// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.internal.statistic.collectors.fus.psi

import com.intellij.internal.statistic.beans.MetricEvent
import com.intellij.internal.statistic.eventLog.EventLogGroup
import com.intellij.internal.statistic.eventLog.events.EventFields
import com.intellij.internal.statistic.service.fus.collectors.ApplicationUsagesCollector
import com.intellij.psi.tree.IElementType

internal class IElementTypeUsageCollector : ApplicationUsagesCollector() {
  override fun getGroup(): EventLogGroup = GROUP

  override fun getMetrics(): Set<MetricEvent> {
    val result = mutableSetOf<MetricEvent>()

    result += ELEMENT_TYPE_COUNT.metric(IElementType.getAllocatedTypesCount().toInt())

    val elementTypes = IElementType.enumerate(IElementType.TRUE)

    val byLanguage = elementTypes
      .asSequence()
      .filterNot { IElementType.isTombstone(it) }
      .groupBy { it.language }
      .mapValues { it.value.size }

    for ((language, count) in byLanguage) {
      result += BY_LANGUAGE_COUNT.metric(language, count)
    }

    result += TOMBSTONE_COUNT.metric(elementTypes.count { IElementType.isTombstone(it) })

    return result
  }
}

private val GROUP = EventLogGroup(
  id = "ielement.type",
  version = 1,
  description = "Collects usage statistics for various aspects of registered IElementTypes"
)

private val ELEMENT_TYPE_COUNT = GROUP.registerEvent(
  eventId = "element_type_count",
  eventField1 = EventFields.RoundedInt("element_type_count"),
  description = "Registered IElementTypes"
)

private val BY_LANGUAGE_COUNT = GROUP.registerEvent(
  eventId = "element_type_by_language",
  eventField1 = EventFields.Language,
  eventField2 = EventFields.RoundedInt("element_type_by_language"),
  description = "Number of registered IElementTypes per Language"
)

private val TOMBSTONE_COUNT = GROUP.registerEvent(
  eventId = "tombstone_element_types",
  eventField1 = EventFields.RoundedInt("tombstone_element_types"),
  description = "Number of tombstone element types"
)
