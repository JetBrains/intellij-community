// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.help.impl

import com.intellij.internal.statistic.eventLog.EventLogGroup
import com.intellij.internal.statistic.eventLog.events.EventFields
import com.intellij.internal.statistic.service.fus.collectors.CounterUsagesCollector
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
object JupyterHelpUsageCollector : CounterUsagesCollector() {
  private val GROUP = EventLogGroup("jupyter.help.usage", 2)
  private val JUPYTER_HELP_TOPICS = listOf(
    "jupyter.settings.page",
    "reference.settings.jupyter",
    "tool_window.jupyter_server_log",
    "reference.jupyter.vcs",
    "how.to.work.with.tables",
    "use.expression.input",
    "adjust.table.formatting",
    "jupyter.data.issues",
    "reference.settings.tables",
    "preferences.database.dataViews"
  )
  private val HELP_ID_FIELD = EventFields.String("help_id", JUPYTER_HELP_TOPICS)
  private val HELP_OPENED_EVENT = GROUP.registerEvent("opened", HELP_ID_FIELD)

  override fun getGroup(): EventLogGroup = GROUP

  @JvmStatic
  fun logOpened(helpId: String?) {
    if (!helpId.isNullOrBlank() && helpId in JUPYTER_HELP_TOPICS) {
      HELP_OPENED_EVENT.log(helpId)
    }
  }
}