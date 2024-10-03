// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.statistic.collectors.fus.settings

import com.intellij.internal.statistic.beans.MetricEvent
import com.intellij.internal.statistic.eventLog.EventLogGroup
import com.intellij.internal.statistic.eventLog.events.EventFields
import com.intellij.internal.statistic.eventLog.events.EventId1
import com.intellij.internal.statistic.service.fus.collectors.ApplicationUsagesCollector
import com.intellij.util.net.HttpConfigurable
import com.intellij.util.net.IdeProxySelector

private class ProxySettingsCollector : ApplicationUsagesCollector() {
  private val GROUP: EventLogGroup = EventLogGroup("proxy.settings", 2)
  private val TYPE: EventId1<String?> = GROUP.registerEvent(
    "proxy.type", EventFields.String("name", ProxyType.entries.map { type -> type.name }))
  private val AUTO_DETECT_DURATION : EventId1<Long> = GROUP.registerEvent("auto.detect.duration", EventFields.DurationMs)

  enum class ProxyType { Auto, Socks, Http }

  override fun getGroup(): EventLogGroup = GROUP

  override fun getMetrics(): MutableSet<MetricEvent> {
    val result = HashSet<MetricEvent>()

    val httpConfigurable = HttpConfigurable.getInstance()
    // HttpProxySettingsUi holds JBRadioButtons for proxy types in ButtonGroups
    // That handles HttpConfigurable flags in sync so we only need to check them one by one to determine currently chosen proxy type
    if (httpConfigurable != null && (httpConfigurable.USE_PROXY_PAC || httpConfigurable.USE_HTTP_PROXY)) {
      val type = when {
        httpConfigurable.USE_PROXY_PAC -> ProxyType.Auto
        /*httpConfigurable.USE_HTTP_PROXY && */httpConfigurable.PROXY_TYPE_IS_SOCKS -> ProxyType.Socks
        /*httpConfigurable.USE_HTTP_PROXY && !httpConfigurable.PROXY_TYPE_IS_SOCKS*/else -> ProxyType.Http
      }
      result.add(TYPE.metric(type.name))
      if (type == ProxyType.Auto) {
        val autoDetectMs = IdeProxySelector.getProxyAutoDetectDurationMs().takeIf { it != -1L }
        if (autoDetectMs != null) result.add(AUTO_DETECT_DURATION.metric(autoDetectMs))
      }
    }

    return result
  }
}