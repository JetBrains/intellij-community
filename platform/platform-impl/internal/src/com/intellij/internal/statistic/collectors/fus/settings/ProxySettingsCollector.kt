// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.internal.statistic.collectors.fus.settings

import com.intellij.internal.statistic.beans.MetricEvent
import com.intellij.internal.statistic.eventLog.EventLogGroup
import com.intellij.internal.statistic.eventLog.events.EventFields
import com.intellij.internal.statistic.service.fus.collectors.ApplicationUsagesCollector
import com.intellij.util.net.IdeProxySelector
import com.intellij.util.net.ProxyConfiguration
import com.intellij.util.net.ProxySettings

internal class ProxySettingsCollector : ApplicationUsagesCollector() {
  private val GROUP = EventLogGroup("proxy.settings", 4)
  private val TYPE = GROUP.registerEvent("proxy.type", EventFields.Enum("name", ProxyType::class.java))
  private val AUTO_DETECT_DURATION = GROUP.registerEvent("auto.detect.duration", EventFields.DurationMs)
  private val LEGACY_AUTH = GROUP.registerEvent("legacy.auth")

  enum class ProxyType { Auto, Socks, Http }

  override fun getGroup(): EventLogGroup = GROUP

  override suspend fun getMetricsAsync(): Set<MetricEvent> {
    val result = HashSet<MetricEvent>()

    val type = when (val config = ProxySettings.getInstance().getProxyConfiguration()) {
      is ProxyConfiguration.ProxyAutoConfiguration -> ProxyType.Auto
      is ProxyConfiguration.StaticProxyConfiguration -> when (config.protocol) {
        ProxyConfiguration.ProxyProtocol.HTTP -> ProxyType.Http
        ProxyConfiguration.ProxyProtocol.SOCKS -> ProxyType.Socks
      }
      else -> null
    }
    if (type != null) {
      result.add(TYPE.metric(type))
      if (type == ProxyType.Auto) {
        val autoDetectMs = IdeProxySelector.getProxyAutoDetectDurationMs()
        if (autoDetectMs >= 0) result += AUTO_DETECT_DURATION.metric(autoDetectMs)
      }
      if (type == ProxyType.Http || type == ProxyType.Socks) {
        @Suppress("DEPRECATION", "removal")
        val legacyAuth = (
          com.intellij.util.net.HttpConfigurable.getInstance().KEEP_PROXY_PASSWORD &&
          com.intellij.util.net.HttpConfigurable.getInstance().readCredentials() != null
        )
        if (legacyAuth) result += LEGACY_AUTH.metric()
      }
    }

    return result
  }
}
