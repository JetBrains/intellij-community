// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.help.impl

import com.intellij.internal.statistic.eventLog.EventLogGroup
import com.intellij.internal.statistic.eventLog.events.EventFields
import com.intellij.internal.statistic.service.fus.collectors.CounterUsagesCollector
import com.intellij.openapi.project.Project
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
object HelpUsageCollector : CounterUsagesCollector() {
  private val GROUP = EventLogGroup("help.usage", 1)
  private val HELP_ID_FIELD = EventFields.StringValidatedByRegexpReference("help_id", "version")
  private val HELP_OPENED_EVENT = GROUP.registerEvent("opened", HELP_ID_FIELD)

  override fun getGroup(): EventLogGroup = GROUP

  @JvmStatic
  fun logOpened(project: Project?, helpId: String?) {
    if (!helpId.isNullOrBlank()) {
      HELP_OPENED_EVENT.log(project, helpId)
    }
  }
}