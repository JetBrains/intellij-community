// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.impl.statistics

import com.intellij.execution.impl.statistics.RunConfigurationTypeUsagesCollector.ID_FIELD
import com.intellij.execution.ui.FragmentStatisticsService
import com.intellij.internal.statistic.eventLog.EventLogGroup
import com.intellij.internal.statistic.eventLog.events.EventFields
import com.intellij.internal.statistic.eventLog.events.FusInputEvent
import com.intellij.internal.statistic.service.fus.collectors.CounterUsagesCollector
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.Project

class RunConfigurationOptionUsagesCollector: CounterUsagesCollector() {
  override fun getGroup() = GROUP

  companion object {
    val GROUP = EventLogGroup("run.configuration.ui.interactions", 3)

    val optionId = EventFields.String("option_id", listOf("before.launch.editSettings", "before.launch.openToolWindow", "beforeRunTasks", "commandLineParameters", "coverage", "doNotBuildBeforeRun", "environmentVariables", "jrePath", "log.monitor", "mainClass", "module.classpath", "redirectInput", "runParallel", "shorten.command.line", "target.project.path", "vmParameters", "workingDirectory",
                                                                "count", "junit.test.kind", "repeat", "testScope", // junit
                                                                "maven.params.workingDir", "maven.params.goals", "maven.params.profiles", "maven.params.resolveToWorkspace",
                                                                "maven.general.useProjectSettings", "maven.general.workOffline", "maven.general.produceExceptionErrorMessages", "maven.general.usePluginRegistry", "maven.general.recursive", "maven.general.alwaysUpdateSnapshots", "maven.general.threadsEditor", "maven.general.outputLevel", "maven.general.checksumPolicy", "maven.general.failPolicy", "maven.general.showDialogWithAdvancedSettings",
                                                                "maven.general.mavenHome", "maven.general.settingsFileOverride.checkbox", "maven.general.settingsFileOverride.text", "maven.general.localRepoOverride.checkbox", "maven.general.localRepoOverride.text",
                                                                "maven.runner.useProjectSettings", "maven.runner.delegateToMaven", "maven.runner.runInBackground", "maven.runner.vmParameters", "maven.runner.envVariables", "maven.runner.jdk", "maven.runner.targetJdk", "maven.runner.skipTests", "maven.runner.properties"
                                                                 ))  // maven
    val projectSettingsAvailableField = EventFields.Boolean("projectSettingsAvailable")
    val useProjectSettingsField = EventFields.Boolean("useProjectSettings")
    val modifyOption = GROUP.registerVarargEvent("modify.run.option", optionId, projectSettingsAvailableField, useProjectSettingsField, ID_FIELD, EventFields.InputEvent)
    val removeOption = GROUP.registerEvent("remove.run.option", optionId, ID_FIELD, EventFields.InputEvent)

    @JvmStatic
    fun logModifyOption(project: Project?, option: String?, config: String?, inputEvent: FusInputEvent?) {
      modifyOption.log(project, optionId.with(option),
                       ID_FIELD.with(config),
                       EventFields.InputEvent.with(inputEvent))
    }

    @JvmStatic
    fun logModifyOption(project: Project?, option: String?, config: String?, projectSettingsAvailable: Boolean, useProjectSettings: Boolean, inputEvent: FusInputEvent?) {
      modifyOption.log(project, optionId.with(option),
                       ID_FIELD.with(config),
                       projectSettingsAvailableField.with(projectSettingsAvailable),
                       useProjectSettingsField.with(useProjectSettings),
                       EventFields.InputEvent.with(inputEvent))
    }


    @JvmStatic
    fun logRemoveOption(project: Project?, option: String?, config: String?, inputEvent: FusInputEvent?) {
      removeOption.log(project, option, config, inputEvent)
    }
  }
}

class FragmentedStatisticsServiceImpl: FragmentStatisticsService() {
  override fun logOptionModified(project: Project?, optionId: String?, runConfigId: String?, inputEvent: AnActionEvent?) {
    RunConfigurationOptionUsagesCollector.logModifyOption(project, optionId, runConfigId, FusInputEvent.from(inputEvent))
  }

  override fun logOptionRemoved(project: Project?, optionId: String?, runConfigId: String?, inputEvent: AnActionEvent?) {
    RunConfigurationOptionUsagesCollector.logRemoveOption(project, optionId, runConfigId, FusInputEvent.from(inputEvent))
  }
}
