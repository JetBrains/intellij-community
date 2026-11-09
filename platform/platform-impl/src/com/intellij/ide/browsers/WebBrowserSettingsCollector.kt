// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.browsers

import com.intellij.internal.statistic.beans.MetricEvent
import com.intellij.internal.statistic.eventLog.EventLogGroup
import com.intellij.internal.statistic.eventLog.events.EventFields
import com.intellij.internal.statistic.service.fus.collectors.ApplicationUsagesCollector

private enum class DefaultBrowserKind {
  SYSTEM_DEFAULT,
  IDE_BROWSER,
  CHROME,
  FIREFOX,
  CUSTOM,
}

private val GROUP = EventLogGroup("web.browsers.settings", 1)
private val BROWSER = EventFields.Enum("browser", DefaultBrowserKind::class.java) { it.name.lowercase() }
private val DEFAULT_BROWSER = GROUP.registerEvent("default.browser", BROWSER)

internal class WebBrowserSettingsCollector : ApplicationUsagesCollector() {
  override fun getGroup(): EventLogGroup = GROUP

  override fun getMetrics(): Set<MetricEvent> {
    val browser = when (WebBrowserManager.getInstance().defaultBrowserId) {
      null -> DefaultBrowserKind.SYSTEM_DEFAULT
      WebBrowserManager.IDE_BROWSER_ID -> DefaultBrowserKind.IDE_BROWSER
      WebBrowserManager.PREDEFINED_CHROME_ID -> DefaultBrowserKind.CHROME
      WebBrowserManager.PREDEFINED_FIREFOX_ID -> DefaultBrowserKind.FIREFOX
      else -> DefaultBrowserKind.CUSTOM
    }
    return setOf(DEFAULT_BROWSER.metric(browser))
  }
}
