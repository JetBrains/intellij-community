// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.externalSystem.statistics

import com.intellij.internal.statistic.service.fus.collectors.FUSProjectUsageTrigger
import com.intellij.internal.statistic.service.fus.collectors.FUSUsageContext
import com.intellij.internal.statistic.service.fus.collectors.ProjectUsageTriggerCollector
import com.intellij.internal.statistic.service.fus.collectors.UsageDescriptorKeyValidator
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.externalSystem.model.ProjectSystemId
import com.intellij.openapi.project.Project

class ExternalSystemActionsCollector : ProjectUsageTriggerCollector() {
  override fun getGroupId(): String {
    return "statistics.build.tools.actions"
  }

  companion object {
    @JvmStatic
    fun trigger(project: Project?,
                systemId: ProjectSystemId?,
                action: AnAction,
                event: AnActionEvent?) {
      trigger(project, systemId, action, event, emptyMap())
    }

    @JvmStatic
    fun trigger(project: Project?,
                systemId: ProjectSystemId?,
                action: AnAction,
                event: AnActionEvent?,
                data: Map<String, String> = emptyMap()) {
      if (project == null) return
      val context = FUSUsageContext.create()
      if (systemId != null) {
        context.data["system"] = escapeSystemId(systemId)
      }
      if (event != null) {
        context.data["place"] = event.place
        context.data["isFromContextMenu"] = event.isFromContextMenu.toString()
      }
      context.data.putAll(data)
      FUSProjectUsageTrigger.getInstance(project).trigger(
        ExternalSystemActionsCollector::class.java,
        UsageDescriptorKeyValidator.ensureProperKey(action.javaClass.simpleName), context)
    }
  }
}
