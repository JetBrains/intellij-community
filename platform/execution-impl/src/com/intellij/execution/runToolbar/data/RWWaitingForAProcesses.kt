// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.runToolbar.data

import com.intellij.execution.RunManager
import com.intellij.execution.RunnerAndConfigurationSettings
import com.intellij.execution.compound.CompoundRunConfiguration
import com.intellij.execution.configurations.RunConfiguration
import com.intellij.openapi.project.Project

class RWWaitingForAProcesses {
  private var executorId: String? = null
  private var settings: RunnerAndConfigurationSettings? = null
  private val subSettingsList: MutableList<RunnerAndConfigurationSettings> = mutableListOf()

  internal fun isWaitingForASingleProcess(settings: RunnerAndConfigurationSettings, executorId: String): Boolean {
    return executorId == this.executorId && settings == this.settings && subSettingsList.isEmpty()
  }

  internal fun isWaitingForASubProcess(settings: RunnerAndConfigurationSettings, executorId: String): Boolean {
    return subSettingsList.contains(settings) && executorId == this.executorId
  }

  internal fun start(project: Project, settings: RunnerAndConfigurationSettings, executorId: String) {
    clear()
    this.executorId = executorId
    this.settings = settings
    if (settings.configuration is CompoundRunConfiguration) {
      collect(project, settings.configuration, settings)
    }
  }

  internal fun clear() {
    settings = null
    executorId = null
    subSettingsList.clear()
  }

  internal fun checkAndUpdate(settings: RunnerAndConfigurationSettings, executorId: String): Boolean {
    if (executorId != this.executorId) return false
    if (subSettingsList.remove(settings)) {
      if (subSettingsList.isEmpty()) clear()
      return true
    }
    return false
  }

  private fun collect(project: Project, configuration: RunConfiguration, settings: RunnerAndConfigurationSettings) {
    if (configuration is CompoundRunConfiguration) {
      val runManager = RunManager.getInstance(project)
      for (settingsAndEffectiveTarget in configuration.getConfigurationsWithEffectiveRunTargets()) {
        val subConfiguration: RunConfiguration = settingsAndEffectiveTarget.configuration
        runManager.findSettings(subConfiguration)?.let {
          collect(project, subConfiguration, it)
        }
      }
    }
    else {
      subSettingsList.add(settings)
    }
  }
}