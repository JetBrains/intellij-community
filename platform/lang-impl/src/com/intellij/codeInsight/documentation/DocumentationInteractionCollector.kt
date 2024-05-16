// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.documentation

import com.intellij.codeInsight.documentation.actions.DocumentationDownloader
import com.intellij.internal.statistic.eventLog.EventLogGroup
import com.intellij.internal.statistic.eventLog.events.EventFields
import com.intellij.internal.statistic.service.fus.collectors.CounterUsagesCollector
import com.intellij.openapi.project.Project
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
object DocumentationInteractionCollector : CounterUsagesCollector() {

  override fun getGroup(): EventLogGroup = GROUP

  private val GROUP = EventLogGroup("documentation.interactions", 1)

  private val HANDLER_FIELD = EventFields.Class("handler")
  private val SUCCESS_FIELD = EventFields.Boolean("success")

  private val DOWNLOAD_FINISHED_EVENT = GROUP.registerEvent("download.finished", HANDLER_FIELD, SUCCESS_FIELD)

  fun logDownloadFinished(project: Project, handlerClass: Class<out DocumentationDownloader>, success: Boolean) {
    DOWNLOAD_FINISHED_EVENT.log(project, handlerClass, success)
  }
}