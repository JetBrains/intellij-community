// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.externalSystem.statistics

import com.intellij.internal.statistic.StructuredIdeActivity
import com.intellij.internal.statistic.eventLog.FeatureUsageData
import com.intellij.internal.statistic.eventLog.events.EventFields
import com.intellij.internal.statistic.eventLog.events.EventPair
import com.intellij.internal.statistic.utils.PluginInfo
import com.intellij.internal.statistic.utils.getPluginInfo
import com.intellij.openapi.externalSystem.model.ProjectSystemId
import com.intellij.openapi.externalSystem.statistics.ExternalSystemActionsCollector.Companion.EXTERNAL_SYSTEM_ID
import com.intellij.openapi.externalSystem.statistics.ProjectImportCollector.Companion.IMPORT_ACTIVITY
import com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil
import com.intellij.openapi.project.Project

fun getAnonymizedSystemId(systemId: ProjectSystemId): String {
  val manager = ExternalSystemApiUtil.getManager(systemId)
  if (manager == null) {
    val isMaven = systemId.id.equals("Maven", ignoreCase = true)
    return if (isMaven) systemId.readableName else "undefined.system"
  }
  return if (getPluginInfo(manager.javaClass).isDevelopedByJetBrains()) systemId.readableName else "third.party"
}

fun addExternalSystemId(data: FeatureUsageData,
                        systemId: ProjectSystemId?) {
  data.addData("system_id", anonymizeSystemId(systemId))
}

fun anonymizeSystemId(systemId: ProjectSystemId?) =
  systemId?.let { getAnonymizedSystemId(it) } ?: "undefined.system"

fun findPluginInfoBySystemId(systemId: ProjectSystemId?): PluginInfo? {
  if (systemId == null) return null
  val manager = ExternalSystemApiUtil.getManager(systemId) ?: return null
  val pluginInfo = getPluginInfo(manager.javaClass)
  return if (pluginInfo.isDevelopedByJetBrains()) pluginInfo else null
}

fun importActivityStarted(project: Project, externalSystemId: ProjectSystemId,
                          dataSupplier: (() -> List<EventPair<*>>)?): StructuredIdeActivity {
  return IMPORT_ACTIVITY.started(project){
    val data: MutableList<EventPair<*>> = mutableListOf(EXTERNAL_SYSTEM_ID.with(anonymizeSystemId(externalSystemId)))
    val pluginInfo = findPluginInfoBySystemId(externalSystemId)
    if (pluginInfo != null) {
      data.add(EventFields.PluginInfo.with(pluginInfo))
    }
    if(dataSupplier != null) {
      data.addAll(dataSupplier())
    }
    data
  }
}