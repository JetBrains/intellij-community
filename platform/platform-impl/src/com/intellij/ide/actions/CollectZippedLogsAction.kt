// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:ApiStatus.Internal

package com.intellij.ide.actions

import com.intellij.logCollector.CollectZippedLogsService
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.components.service
import com.intellij.openapi.project.DumbAware
import org.jetbrains.annotations.ApiStatus

const val COLLECT_LOGS_NOTIFICATION_GROUP: String = "Collect Zipped Logs"

internal class CollectZippedLogsAction : AnAction(), DumbAware {
  override fun actionPerformed(e: AnActionEvent) {
    val project = e.project

    service<CollectZippedLogsService>().collectZippedLogs(project)
  }


  override fun getActionUpdateThread(): ActionUpdateThread {
    return ActionUpdateThread.BGT
  }
}
