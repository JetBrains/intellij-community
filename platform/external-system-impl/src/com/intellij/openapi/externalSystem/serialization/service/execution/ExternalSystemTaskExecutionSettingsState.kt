// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.externalSystem.serialization.service.execution

import com.intellij.openapi.externalSystem.model.execution.ExternalSystemTaskExecutionSettings
import com.intellij.openapi.externalSystem.model.execution.TaskSettings
import com.intellij.openapi.externalSystem.model.execution.TaskSettingsImpl
import com.intellij.openapi.util.text.StringUtil
import com.intellij.util.containers.ContainerUtilRt
import com.intellij.util.xmlb.annotations.Tag

@Tag("ExternalSystemSettings")
class ExternalSystemTaskExecutionSettingsState(
  var tasksSettings: MutableList<TaskSettingsState>,
  var taskNames: MutableList<String>,
  var taskDescriptions: MutableList<String>,
  var executionName: String?,
  var externalSystemIdString: String?,
  var externalProjectPath: String?,
  var vmOptions: String?,
  var scriptParameters: String?,
  var unorderedArguments: MutableSet<String>,
  var env: MutableMap<String, String>,
  var isPassParentEnvs: Boolean
) {

  @Suppress("unused")
  constructor() : this(
    tasksSettings = ContainerUtilRt.newArrayList(),
    taskNames = ContainerUtilRt.newArrayList(),
    taskDescriptions = ContainerUtilRt.newArrayList(),
    executionName = null,
    externalSystemIdString = null,
    externalProjectPath = null,
    vmOptions = null,
    scriptParameters = null,
    unorderedArguments = ContainerUtilRt.newLinkedHashSet(),
    env = ContainerUtilRt.newHashMap(),
    isPassParentEnvs = true
  )

  fun toTaskExecutionSettings(): ExternalSystemTaskExecutionSettings {
    val settings = ExternalSystemTaskExecutionSettings()
    settings.taskNames = taskNames
    settings.taskDescriptions = taskDescriptions
    settings.executionName = executionName
    settings.externalSystemIdString = externalSystemIdString
    settings.externalProjectPath = externalProjectPath
    settings.vmOptions = vmOptions
    settings.scriptParameters = scriptParameters
    settings.env = env
    settings.isPassParentEnvs = isPassParentEnvs
    for (taskSettings in tasksSettings) {
      settings.addTaskSettings(taskSettings.toTaskSettings())
    }
    for (argument in unorderedArguments) {
      settings.addUnorderedArgument(argument)
    }
    return settings
  }

  companion object {
    const val TAG_NAME = "ExternalSystemSettings"

    private fun ExternalSystemTaskExecutionSettings.getZipTask(): TaskSettings? {
      repairSettingsIfNeeded()
      if (rawZipTaskArguments.isEmpty()) return null
      val zipTaskName = taskNames.lastOrNull() ?: return null
      return TaskSettingsImpl(zipTaskName, rawZipTaskArguments)
    }

    @JvmStatic
    fun valueOf(settings: ExternalSystemTaskExecutionSettings): ExternalSystemTaskExecutionSettingsState {
      val zipTask = settings.getZipTask()
      val tasksSettings = ArrayList<TaskSettings>()
      if (zipTask != null) tasksSettings.add(zipTask)
      tasksSettings.addAll(settings.rawTasksSettings)
      val tasksSettingStates = tasksSettings.map(TaskSettingsState.Companion::valueOf)
      val taskNames = when (zipTask) {
        null -> settings.taskNames
        else -> settings.taskNames.dropLast(1)
      }
      return ExternalSystemTaskExecutionSettingsState(
        tasksSettings = tasksSettingStates.toMutableList(),
        taskNames = taskNames.toMutableList(),
        taskDescriptions = settings.taskDescriptions,
        executionName = settings.executionName,
        externalSystemIdString = settings.externalSystemIdString,
        externalProjectPath = settings.externalProjectPath,
        vmOptions = settings.vmOptions,
        scriptParameters = settings.rawScriptParameters,
        unorderedArguments = settings.rawUnorderedArguments,
        env = settings.env,
        isPassParentEnvs = settings.isPassParentEnvs
      )
    }
  }
}
