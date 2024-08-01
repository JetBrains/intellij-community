// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.util.projectWizard

import com.intellij.internal.statistic.eventLog.EventLogGroup
import com.intellij.internal.statistic.eventLog.events.EventFields
import com.intellij.internal.statistic.service.fus.collectors.CounterUsagesCollector
import com.intellij.internal.statistic.utils.getPluginInfo
import com.intellij.openapi.project.Project

/**
 * Collects FUS statistics for the lightweight new project wizard.
 * This new project wizard is used in non-Java IDEs like PyCharm, WebStorm, RustRover, etc.
 *
 * @see com.intellij.ide.util.projectWizard.AbstractNewProjectDialog
 */
internal object LightweightNewProjectWizardCollector : CounterUsagesCollector() {

  override fun getGroup(): EventLogGroup = GROUP

  private val GROUP: EventLogGroup = EventLogGroup("new.project.wizard", 4)

  private val projectGeneratedEvent = GROUP.registerEvent("project.generated",
                                                          EventFields.Class("generator_id"),
                                                          EventFields.PluginInfo)

  @JvmStatic
  fun logProjectGenerated(project: Project?, generator: Class<*>?) {
    projectGeneratedEvent.log(project, generator, generator?.let { getPluginInfo(it) })
  }
}