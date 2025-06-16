// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.internal.statistic.collectors.fus.settings

import com.intellij.internal.statistic.beans.MetricEvent
import com.intellij.internal.statistic.eventLog.EventLogGroup
import com.intellij.internal.statistic.eventLog.events.EventFields
import com.intellij.internal.statistic.service.fus.collectors.ApplicationUsagesCollector
import com.intellij.openapi.application.PathManager
import java.nio.file.Files
import java.nio.file.Path

internal class SystemPropertiesFileCollector : ApplicationUsagesCollector() {
  private val GROUP = EventLogGroup("sys.properties.file", 1)

  private val LOCATIONS = GROUP.registerEvent(
    "locations", EventFields.Boolean("custom"), EventFields.Boolean("explicit"), EventFields.Boolean("user_home"))

  override fun getGroup(): EventLogGroup = GROUP

  override fun getMetrics(): Set<MetricEvent> = setOf(
    LOCATIONS.metric(
      Files.isRegularFile(PathManager.getConfigDir().resolve(PathManager.PROPERTIES_FILE_NAME)),
      System.getProperty(PathManager.PROPERTIES_FILE) != null,  // a file might be absent, but an attempt counts
      Files.isRegularFile(Path.of(System.getProperty("user.home"), PathManager.PROPERTIES_FILE_NAME))
    )
  )
}
