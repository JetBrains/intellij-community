// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.database.csv

import com.intellij.internal.statistic.eventLog.EventLogGroup
import com.intellij.internal.statistic.eventLog.events.EventFields
import com.intellij.internal.statistic.service.fus.collectors.CounterUsagesCollector
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile

object CsvFileUsageCollector : CounterUsagesCollector() {
  override fun getGroup(): EventLogGroup = GROUP

  private val GROUP = EventLogGroup("csv.file.usage", 1)
  private val FILE_SIZE_FIELD = EventFields.RoundedLong("file_size_bytes")
  private val OPEN_MODE_FIELD = EventFields.Enum<OpenMode>("open_mode")
  private val OPEN_CSV_FILE_EVENT = GROUP.registerVarargEvent("opened", FILE_SIZE_FIELD, OPEN_MODE_FIELD)

  enum class OpenMode {
    DATA, TEXT
  }

  @JvmStatic
  fun logFileOpened(project: Project, file: VirtualFile, mode: OpenMode) {
    OPEN_CSV_FILE_EVENT.log(project, FILE_SIZE_FIELD.with(file.length), OPEN_MODE_FIELD.with(mode))
  }
}
