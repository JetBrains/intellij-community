// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.impl.statistics

import com.intellij.execution.impl.statistics.RunConfigurationTypeUsagesCollector.ID_FIELD
import com.intellij.execution.ui.FragmentStatisticsService
import com.intellij.internal.statistic.eventLog.EventLogGroup
import com.intellij.internal.statistic.eventLog.events.EventFields
import com.intellij.internal.statistic.eventLog.events.FusInputEvent
import com.intellij.internal.statistic.service.fus.collectors.CounterUsagesCollector
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.Project
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
object RunConfigurationOptionUsagesCollector : CounterUsagesCollector() {
  override fun getGroup() = GROUP

  val GROUP = EventLogGroup("run.configuration.ui.interactions", 17)

  val optionId = EventFields.String("option_id", listOf("before.launch.editSettings", "before.launch.openToolWindow", "beforeRunTasks",
                                                        "commandLineParameters", "coverage", "doNotBuildBeforeRun", "environmentVariables",
                                                        "jrePath", "log.monitor", "mainClass", "module.classpath", "redirectInput",
                                                        "runParallel", "shorten.command.line", "target.project.path", "vmParameters",
                                                        "workingDirectory",
                                                        "count", "junit.test.kind", "repeat", "testScope", // junit
                                                        "maven.params.workingDir", "maven.params.goals", "maven.params.profiles",
                                                        "maven.params.resolveToWorkspace",
                                                        "maven.general.useProjectSettings", "maven.general.workOffline",
                                                        "maven.general.produceExceptionErrorMessages", "maven.general.usePluginRegistry",
                                                        "maven.general.recursive", "maven.general.alwaysUpdateSnapshots",
                                                        "maven.general.threadsEditor", "maven.general.outputLevel",
                                                        "maven.general.checksumPolicy", "maven.general.failPolicy",
                                                        "maven.general.showDialogWithAdvancedSettings",
                                                        "maven.general.mavenHome", "maven.general.settingsFileOverride.checkbox",
                                                        "maven.general.settingsFileOverride.text",
                                                        "maven.general.localRepoOverride.checkbox", "maven.general.localRepoOverride.text",
                                                        "maven.runner.useProjectSettings", "maven.runner.delegateToMaven",
                                                        "maven.runner.runInBackground", "maven.runner.vmParameters",
                                                        "maven.runner.envVariables", "maven.runner.jdk", "maven.runner.targetJdk",
                                                        "maven.runner.skipTests", "maven.runner.properties",
                                                        "Dump_file_path", "Exe_path", "Program_arguments", "Working_directory",
                                                        "Environment_variables", "Runtime_arguments", "Use_Mono_runtime",
                                                        "Use_external_console", "Project", "Target_framework", "Launch_profile",
                                                        "Open_browser", "Application_URL", "Launch_URL", "IIS_Express_Certificate",
                                                        "Hosting_model", "Generate_applicationhost.config", "Show_IIS_Express_output",
                                                        "Send_debug_request", "Additional_IIS_Express_arguments", "Static_method", "URL",
                                                        "Session_name", "Arguments", "Solution_Configuration", "Executable_file",
                                                        "Default_arguments", "Optional_arguments", "browser.option.after.launch",
                                                        "browser.option.with.javascript.debugger", "browser.option.target.browser",
                                                        "Use_Hot_Reload", "Roslyn_Target_Project", "Extra_mlaunch_Parameters",
                                                        "Project_Configuration", "Launch_profile_group", "Platform_Type", // Rider
                                                        "external.system.vm.parameters.fragment", "Runtime"
  ))  // maven
  private val projectSettingsAvailableField = EventFields.Boolean("projectSettingsAvailable")
  private val useProjectSettingsField = EventFields.Boolean("useProjectSettings")
  private val modifyOption = GROUP.registerVarargEvent("modify.run.option", optionId, projectSettingsAvailableField,
                                                       useProjectSettingsField, ID_FIELD, EventFields.InputEvent)
  private val navigateOption = GROUP.registerVarargEvent("option.navigate", optionId, ID_FIELD, EventFields.InputEvent)
  private val removeOption = GROUP.registerEvent("remove.run.option", optionId, ID_FIELD, EventFields.InputEvent)
  private val addNew = GROUP.registerEvent("add", ID_FIELD, EventFields.ActionPlace)
  val copy = GROUP.registerEvent("copy", ID_FIELD, EventFields.ActionPlace)
  val remove = GROUP.registerEvent("remove", ID_FIELD, EventFields.ActionPlace)
  private val hintsShown = GROUP.registerEvent("hints.shown", ID_FIELD, EventFields.Int("hint_number"), EventFields.DurationMs)

  private val addBeforeRunTask = GROUP.registerEvent("before.run.task.add", ID_FIELD, EventFields.Class("providerClass"))
  private val editBeforeRunTask = GROUP.registerEvent("before.run.task.edit", ID_FIELD, EventFields.Class("providerClass"))
  private val removeBeforeRunTask = GROUP.registerEvent("before.run.task.remove", ID_FIELD, EventFields.Class("providerClass"))

  @JvmStatic
  fun logAddBeforeRunTask(project: Project?, configurationTypeId: String?, providerClass: Class<*>) {
    addBeforeRunTask.log(project, configurationTypeId, providerClass)
  }

  @JvmStatic
  fun logEditBeforeRunTask(project: Project?, configurationTypeId: String?, providerClass: Class<*>) {
    editBeforeRunTask.log(project, configurationTypeId, providerClass)
  }

  @JvmStatic
  fun logRemoveBeforeRunTask(project: Project?, configurationTypeId: String?, providerClass: Class<*>) {
    removeBeforeRunTask.log(project, configurationTypeId, providerClass)
  }

  @JvmStatic
  fun logAddNew(project: Project?, configurationTypeId: String?, place: String?) {
    addNew.log(project, configurationTypeId, place)
  }

  @JvmStatic
  fun logCopy(project: Project?, configurationTypeId: String?, place: String?) {
    copy.log(project, configurationTypeId, place)
  }

  @JvmStatic
  fun logRemove(project: Project?, configurationTypeId: String?, place: String?) {
    remove.log(project, configurationTypeId, place)
  }

  @JvmStatic
  fun logModifyOption(project: Project?, option: String?, config: String?, inputEvent: FusInputEvent?) {
    modifyOption.log(project, optionId.with(option),
                     ID_FIELD.with(config),
                     EventFields.InputEvent.with(inputEvent))
  }

  @JvmStatic
  fun logNavigateOption(project: Project?, option: String?, config: String?, inputEvent: FusInputEvent?) {
    navigateOption.log(project, optionId.with(option),
                       ID_FIELD.with(config),
                       EventFields.InputEvent.with(inputEvent))
  }

  @JvmStatic
  fun logModifyOption(project: Project?,
                      option: String?,
                      config: String?,
                      projectSettingsAvailable: Boolean,
                      useProjectSettings: Boolean,
                      inputEvent: FusInputEvent?) {
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

  @JvmStatic
  fun logShowHints(project: Project?, config: String?, hintsCount: Int, duration: Long) {
    hintsShown.log(project, config, hintsCount, duration)
  }
}

@ApiStatus.Internal
class FragmentedStatisticsServiceImpl : FragmentStatisticsService() {
  override fun logOptionModified(project: Project?, optionId: String?, runConfigId: String?, inputEvent: AnActionEvent?) {
    RunConfigurationOptionUsagesCollector.logModifyOption(project, optionId, runConfigId, FusInputEvent.from(inputEvent))
  }

  override fun logOptionRemoved(project: Project?, optionId: String?, runConfigId: String?, inputEvent: AnActionEvent?) {
    RunConfigurationOptionUsagesCollector.logRemoveOption(project, optionId, runConfigId, FusInputEvent.from(inputEvent))
  }

  override fun logNavigateOption(project: Project?, optionId: String?, runConfigId: String?, inputEvent: AnActionEvent?) {
    RunConfigurationOptionUsagesCollector.logNavigateOption(project, optionId, runConfigId, FusInputEvent.from(inputEvent))
  }

  override fun logHintsShown(project: Project?, runConfigId: String?, hintNumber: Int, duration: Long) {
    RunConfigurationOptionUsagesCollector.logShowHints(project, runConfigId, hintNumber, duration)
  }
}
