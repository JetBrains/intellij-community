// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.compiler

import com.intellij.internal.statistic.beans.MetricEvent
import com.intellij.internal.statistic.eventLog.EventLogGroup
import com.intellij.internal.statistic.eventLog.events.EventFields
import com.intellij.internal.statistic.service.fus.collectors.ProjectUsagesCollector
import com.intellij.openapi.project.Project

class CompilerSettingsCollector : ProjectUsagesCollector() {

  override fun getGroup(): EventLogGroup {
    return GROUP
  }

  override fun getMetrics(project: Project): MutableSet<MetricEvent> {
    val usages = mutableSetOf<MetricEvent>()

    val workspaceConfig = CompilerWorkspaceConfiguration.getInstance(project)
    val config = CompilerConfiguration.getInstance(project)

    usages.add(AUTO_SHOW_ERRORS_IN_EDITOR.metric(workspaceConfig.AUTO_SHOW_ERRORS_IN_EDITOR))
    usages.add(DISPLAY_NOTIFICATION_POPUP.metric(workspaceConfig.DISPLAY_NOTIFICATION_POPUP))
    usages.add(CLEAR_OUTPUT_DIRECTORY.metric(workspaceConfig.CLEAR_OUTPUT_DIRECTORY))
    usages.add(MAKE_PROJECT_ON_SAVE.metric(workspaceConfig.MAKE_PROJECT_ON_SAVE))
    usages.add(PARALLEL_COMPILATION.metric(config.isParallelCompilationEnabled))
    usages.add(REBUILD_ON_DEPENDENCY_CHANGE.metric(workspaceConfig.REBUILD_ON_DEPENDENCY_CHANGE))
    usages.add(COMPILE_AFFECTED_UNLOADED_MODULES_BEFORE_COMMIT.metric(workspaceConfig.COMPILE_AFFECTED_UNLOADED_MODULES_BEFORE_COMMIT))

    return usages
  }

  companion object {
    private val GROUP = EventLogGroup("java.compiler.settings.project", 2)
    private val AUTO_SHOW_ERRORS_IN_EDITOR = GROUP.registerEvent("AUTO_SHOW_ERRORS_IN_EDITOR", EventFields.Enabled)
    private val DISPLAY_NOTIFICATION_POPUP = GROUP.registerEvent("DISPLAY_NOTIFICATION_POPUP", EventFields.Enabled)
    private val CLEAR_OUTPUT_DIRECTORY = GROUP.registerEvent("CLEAR_OUTPUT_DIRECTORY", EventFields.Enabled)
    private val MAKE_PROJECT_ON_SAVE = GROUP.registerEvent("MAKE_PROJECT_ON_SAVE", EventFields.Enabled)
    private val PARALLEL_COMPILATION = GROUP.registerEvent("PARALLEL_COMPILATION", EventFields.Enabled)
    private val REBUILD_ON_DEPENDENCY_CHANGE = GROUP.registerEvent("REBUILD_ON_DEPENDENCY_CHANGE", EventFields.Enabled)
    private val COMPILE_AFFECTED_UNLOADED_MODULES_BEFORE_COMMIT = GROUP.registerEvent("COMPILE_AFFECTED_UNLOADED_MODULES_BEFORE_COMMIT", EventFields.Enabled)
  }
}
