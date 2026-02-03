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

    val elementTypes = IElementType.enumerate(IElementType.TRUE)

    result += ELEMENT_TYPES.metric(value1 = IElementType.getAllocatedTypesCount().toInt(),
                                   value2 = elementTypes.count { IElementType.isTombstone(it) })

    val byLanguage = elementTypes
      .asSequence()
      .filterNot { IElementType.isTombstone(it) }
      .groupBy { it.language }
      .mapValues { it.value.size }

    for ((language, count) in byLanguage) {
      result += ELEMENT_TYPES_BY_LANGUAGE_COUNT.metric(language, count)
    }

    return result
  }
}

private val GROUP = EventLogGroup(
  id = "ielement.type",
  version = 2,
  description = "Collects usage statistics for various aspects of registered IElementTypes"
)

private val ELEMENT_TYPES = GROUP.registerEvent(
  eventId = "element_types",
  eventField1 = EventFields.RoundedInt("all_element_type_count"),
  eventField2 = EventFields.RoundedInt("tombstone_count"),
  description = "Registered IElementTypes"
)

private val ELEMENT_TYPES_BY_LANGUAGE_COUNT = GROUP.registerEvent(
  eventId = "element_type_by_language",
  eventField1 = EventFields.Language,
  eventField2 = EventFields.RoundedInt("count"),
  description = "Number of registered IElementTypes per Language"
)
