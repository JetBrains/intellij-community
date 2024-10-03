package com.intellij.execution.multilaunch.statistics

import com.intellij.execution.impl.statistics.RunConfigurationUsageTriggerCollector
import com.intellij.internal.statistic.eventLog.events.EventField
import com.intellij.internal.statistic.service.fus.collectors.FeatureUsageCollectorExtension
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
class MultiLaunchConfigurationUsageCollectorExtension : FeatureUsageCollectorExtension {
  override fun getGroupId() = RunConfigurationUsageTriggerCollector.GROUP_NAME

  override fun getEventId() = "started"

  override fun getExtensionFields(): List<EventField<*>> = listOf(
    FusExecutableRows.FIELD,
    MultiLaunchEventFields.ACTIVATE_TOOL_WINDOWS_FIELD)
}

