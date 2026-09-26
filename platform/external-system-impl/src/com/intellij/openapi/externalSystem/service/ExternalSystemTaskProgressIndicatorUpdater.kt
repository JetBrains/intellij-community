// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.externalSystem.service

import com.intellij.build.events.ProgressBuildEvent
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.externalSystem.model.ProjectSystemId
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskId
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskNotificationEvent
import com.intellij.openapi.externalSystem.model.task.event.ExternalSystemBuildEvent
import com.intellij.openapi.externalSystem.model.task.event.ExternalSystemStatusEvent
import com.intellij.openapi.externalSystem.model.task.event.ExternalSystemTaskExecutionEvent
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.util.NlsContexts
import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.util.text.StringUtil.formatFileSize
import com.intellij.openapi.util.text.StringUtil.isNotEmpty
import java.util.function.Function

abstract class ExternalSystemTaskProgressIndicatorUpdater {

  companion object {
    val EP_NAME: ExtensionPointName<ExternalSystemTaskProgressIndicatorUpdater> = ExtensionPointName.create(
      "com.intellij.externalSystemTaskProgressIndicatorUpdater")

    @JvmStatic
    fun getInstance(systemId: ProjectSystemId): ExternalSystemTaskProgressIndicatorUpdater? = EP_NAME.findFirstSafe {
      it.canUpdate(systemId)
    }

    @JvmStatic
    fun getInstanceOrDefault(systemId: ProjectSystemId): ExternalSystemTaskProgressIndicatorUpdater {
      return getInstance(systemId)
             ?: object : ExternalSystemTaskProgressIndicatorUpdater() {
               override fun canUpdate(externalSystemId: ProjectSystemId): Boolean = true
             }
    }
  }

  abstract fun canUpdate(externalSystemId: ProjectSystemId): Boolean

  open fun updateIndicator(event: ExternalSystemTaskNotificationEvent,
                           indicator: ProgressIndicator,
                           textWrapper: Function<String, @NlsContexts.ProgressText String>) {
    val total: Long
    val progress: Long
    val unit: String
    when {
      event is ExternalSystemBuildEvent && event.buildEvent is ProgressBuildEvent -> {
        val progressEvent = event.buildEvent as ProgressBuildEvent
        total = progressEvent.getTotal()
        progress = progressEvent.getProgress()
        unit = progressEvent.getUnit()
      }
      event is ExternalSystemTaskExecutionEvent && event.progressEvent is ExternalSystemStatusEvent<*> -> {
        val progressEvent = event.progressEvent as ExternalSystemStatusEvent<*>
        total = progressEvent.total
        progress = progressEvent.progress
        unit = progressEvent.unit
      }
      else -> return
    }
    if (total <= 0) {
      indicator.setIndeterminate(true)
    }
    else {
      indicator.setIndeterminate(false)
      indicator.setFraction(progress.toDouble() / total.toDouble())
    }
    val description = event.description
    indicator.setText(getText(description, progress, total, unit, textWrapper))
  }

  @NlsSafe
  open fun getText(description: String,
                   progress: Long,
                   total: Long,
                   unit: String,
                   textWrapper: Function<String, @NlsContexts.ProgressText String>): String {
    val body = textWrapper.apply(description)
    val tail = getSizeInfo(progress, total, unit).let { if (isNotEmpty(it)) return@let "($it)" else "" }
    return "$body $tail"
  }

  open fun onTaskEnd(taskId: ExternalSystemTaskId): Unit = Unit

  @NlsSafe
  private fun getSizeInfo(progress: Long, total: Long, unit: String): String = when (unit) {
    "bytes" -> if (total <= 0) "${formatFileSize(progress)} / ?" else "${formatFileSize(progress)} / ${formatFileSize(total)}"
    "items" -> if (total <= 0) "$progress / ?" else "$progress / $total"
    else -> ""
  }
}