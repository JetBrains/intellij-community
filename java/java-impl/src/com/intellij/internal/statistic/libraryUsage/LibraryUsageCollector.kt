// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.internal.statistic.libraryUsage

import com.intellij.internal.statistic.eventLog.EventLogGroup
import com.intellij.internal.statistic.eventLog.events.EventFields
import com.intellij.internal.statistic.service.fus.collectors.CounterUsagesCollector
import com.intellij.openapi.fileTypes.FileType
import com.intellij.openapi.project.Project

internal class LibraryUsageCollector : CounterUsagesCollector() {
  override fun getGroup(): EventLogGroup = GROUP

  companion object {
    private val GROUP = EventLogGroup("libraryUsage", 2)
    private val EVENT = GROUP.registerEvent(
      eventId = "library_used",
      eventField1 = EventFields.String("library_name", emptyList()), // TODO: workaround. Fix after IDEA-279202
      eventField2 = EventFields.Version,
      eventField3 = EventFields.FileType,
    )

    fun log(project: Project, libraryUsage: LibraryUsage) {
      EVENT.log(project = project, value1 = libraryUsage.name, value2 = libraryUsage.version, value3 = libraryUsage.fileType)
    }
  }
}

class LibraryUsage(val name: String, val version: String, val fileType: FileType) {
  override fun toString(): String = "$name-$version for ${fileType.name}"
}
