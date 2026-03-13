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

private val GROUP = EventLogGroup("ielement.type", 2)

private val ELEMENT_TYPES = GROUP.registerEvent(
  "element_types",
  EventFields.RoundedInt("all_element_type_count"), EventFields.RoundedInt("tombstone_count")
)

private val ELEMENT_TYPES_BY_LANGUAGE_COUNT = GROUP.registerEvent(
  "element_type_by_language",
  EventFields.Language, EventFields.RoundedInt("count")
)
