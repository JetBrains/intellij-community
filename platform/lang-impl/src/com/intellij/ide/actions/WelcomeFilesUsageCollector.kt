// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.actions

import com.intellij.internal.statistic.eventLog.EventLogGroup
import com.intellij.internal.statistic.eventLog.events.EventFields
import com.intellij.internal.statistic.service.fus.collectors.CounterUsagesCollector
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.MessageConstants.YesNoCancelResult
import com.intellij.openapi.ui.Messages

internal object WelcomeFilesUsageCollector : CounterUsagesCollector() {
  override fun getGroup(): EventLogGroup = GROUP

  private val GROUP = EventLogGroup("welcome.files", 1)

  private val RESULT = EventFields.Enum<CloseDialogResult>("result")
  private val FILES_COUNT = EventFields.RoundedInt("files_count")
  private val CLOSE_DIALOG_CLOSED = GROUP.registerEvent("close.dialog.closed", RESULT, FILES_COUNT)

  fun logCloseDialogClosed(project: Project, @YesNoCancelResult result: Int, filesCount: Int) {
    val dialogResult = when (result) {
      Messages.OK -> CloseDialogResult.SAVE
      Messages.NO -> CloseDialogResult.DONT_SAVE
      else -> CloseDialogResult.CANCEL
    }
    CLOSE_DIALOG_CLOSED.log(project, dialogResult, filesCount)
  }

  private enum class CloseDialogResult { SAVE, DONT_SAVE, CANCEL }
}
