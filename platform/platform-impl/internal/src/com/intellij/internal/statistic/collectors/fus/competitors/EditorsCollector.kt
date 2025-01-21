// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.internal.statistic.collectors.fus.competitors

import com.intellij.internal.statistic.beans.MetricEvent
import com.intellij.internal.statistic.eventLog.EventLogGroup
import com.intellij.internal.statistic.eventLog.events.EventFields
import com.intellij.internal.statistic.eventLog.events.EventId1
import com.intellij.internal.statistic.service.fus.collectors.ApplicationUsagesCollector
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.nio.file.Files
import java.nio.file.Paths

internal class EditorsCollector :  ApplicationUsagesCollector() {
  private val EDITORS_GROUP: EventLogGroup = EventLogGroup("editors", 1)

  override fun getGroup(): EventLogGroup = EDITORS_GROUP

  private val CONFIGS: List<String> = listOf(
    ".vimrc", // Settings for Vim-like editors. Actual file may be different, e.g. `_vimrc` on Windows.
  )

  private val CONFIG_EXISTS: EventId1<String> = EDITORS_GROUP.registerEvent(
    "config.exists",
    EventFields.String("config", CONFIGS)
  )

  override suspend fun getMetricsAsync(): Set<MetricEvent> {
    val homeDir = System.getProperty("user.home")
    return withContext(Dispatchers.IO) {
      buildSet {
        // Vim configuration files
        if (
          Files.exists(Paths.get(homeDir, ".vimrc")) ||
          Files.exists(Paths.get(homeDir, "_vimrc")) ||
          Files.exists(Paths.get(homeDir, ".vim/vimrc"))
        ) {
          add(CONFIG_EXISTS.metric(".vimrc"))
        }
      }
    }
  }
}
