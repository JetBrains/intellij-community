// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.accessibility

import com.intellij.ide.GeneralSettings
import com.intellij.internal.statistic.beans.MetricEvent
import com.intellij.internal.statistic.eventLog.EventLogGroup
import com.intellij.internal.statistic.eventLog.events.EventFields
import com.intellij.internal.statistic.service.fus.collectors.ApplicationUsagesCollector

internal class AccessibilityStateCollector : ApplicationUsagesCollector() {
  private val group = EventLogGroup("accessibility.state", 1)
  private val screenReaderSupportInVmOptions = group.registerEvent("screen.reader.support.enabled.in.vmoptions", EventFields.Boolean("enabled"))

  override fun getGroup(): EventLogGroup = group

  override fun getMetrics(): Set<MetricEvent> {
    val enabled = System.getProperty(GeneralSettings.SUPPORT_SCREEN_READERS)?.toBoolean() ?: return emptySet()
    return setOf(screenReaderSupportInVmOptions.metric(enabled))
  }
}
