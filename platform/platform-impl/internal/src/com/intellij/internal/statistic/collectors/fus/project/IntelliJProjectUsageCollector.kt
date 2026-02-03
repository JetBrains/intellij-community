// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.statistic.collectors.fus.project

import com.intellij.internal.statistic.beans.MetricEvent
import com.intellij.internal.statistic.collectors.fus.RegistryApplicationUsagesCollector
import com.intellij.internal.statistic.eventLog.EventLogGroup
import com.intellij.internal.statistic.eventLog.events.EventFields
import com.intellij.internal.statistic.eventLog.events.EventId1
import com.intellij.internal.statistic.service.fus.collectors.ProjectUsagesCollector
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.registry.Registry
import org.jetbrains.annotations.ApiStatus

/**
 * @author Konstantin Bulenkov
 */
internal class IntelliJProjectUsageCollector : ProjectUsagesCollector() {
  private val GROUP = EventLogGroup("project.intellij.monorepo", 1)
  private val INTELLIJ_PROJECT: EventId1<Boolean> = GROUP.registerEvent("is.intellij", EventFields.Enabled)

  override fun getGroup(): EventLogGroup = GROUP

  override fun getMetrics(project: Project): MutableSet<MetricEvent> {
    return mutableSetOf<MetricEvent>().apply {
      if (isIdeaProject(project)) {
        add(INTELLIJ_PROJECT.metric(true))
      }
    }
  }
}

@ApiStatus.Internal
fun isIdeaProject(project: Project): Boolean {
  if (Registry.`is`(RegistryApplicationUsagesCollector.DISABLE_INTELLIJ_PROJECT_ANALYTICS)) return false

  val moduleManager = ModuleManager.getInstance(project)
  return moduleManager.findModuleByName("intellij.platform.commercial") != null
         && moduleManager.findModuleByName("intellij.platform.commercial.verifier") != null
}
