// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.internal.statistic.collectors.fus.environment

import com.intellij.internal.statistic.beans.MetricEvent
import com.intellij.internal.statistic.eventLog.EventLogGroup
import com.intellij.internal.statistic.eventLog.events.EventFields
import com.intellij.internal.statistic.eventLog.events.EventId1
import com.intellij.internal.statistic.service.fus.collectors.ApplicationUsagesCollector
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.nio.file.Files
import java.nio.file.Paths

private const val VIMRC_ID = ".vimrc"
private const val VSCODE_ID = ".vscode"
private const val CURSOR_ID = ".cursor"
private const val WINDSURF_ID = ".windsurf"
private const val ECLIPSE_ID = ".eclipse"

internal class EditorsCollector :  ApplicationUsagesCollector() {
  private val EDITORS_GROUP: EventLogGroup = EventLogGroup("editors", 2)

  override fun getGroup(): EventLogGroup = EDITORS_GROUP

  private val CONFIGS: List<String> = listOf(
    VIMRC_ID,
    VSCODE_ID,
    CURSOR_ID,
    WINDSURF_ID,
    ECLIPSE_ID
  )

  private val CONFIG_EXISTS: EventId1<String> = EDITORS_GROUP.registerEvent(
    "config.exists",
    EventFields.String("config", CONFIGS)
  )

  override suspend fun getMetricsAsync(): Set<MetricEvent> {
    val homeDir = System.getProperty("user.home")
    return withContext(Dispatchers.IO) {
      buildSet {
        if (
          Files.exists(Paths.get(homeDir, ".vimrc")) ||
          Files.exists(Paths.get(homeDir, "_vimrc")) ||
          Files.exists(Paths.get(homeDir, ".vim/vimrc"))
        ) {
          add(CONFIG_EXISTS.metric(VIMRC_ID))
        }

        if (Files.isDirectory(Paths.get(homeDir, ".vscode"))) {
          add(CONFIG_EXISTS.metric(VSCODE_ID))
        }

        if (Files.isDirectory(Paths.get(homeDir, ".cursor"))) {
          add(CONFIG_EXISTS.metric(CURSOR_ID))
        }

        if (Files.isDirectory(Paths.get(homeDir, ".windsurf"))) {
          add(CONFIG_EXISTS.metric(WINDSURF_ID))
        }

        if (Files.isDirectory(Paths.get(homeDir, ".eclipse"))) {
          add(CONFIG_EXISTS.metric(ECLIPSE_ID))
        }
      }
    }
  }
}
