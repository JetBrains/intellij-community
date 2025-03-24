// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.runners

import com.intellij.execution.ExecutionManager
import com.intellij.execution.RunnerAndConfigurationSettings
import com.intellij.execution.dashboard.RunDashboardManager
import com.intellij.openapi.util.NlsSafe
import javax.swing.Icon

internal class BackendExecutionEnvironmentProxy(private val environment: ExecutionEnvironment) : ExecutionEnvironmentProxy {
  override fun isShowInDashboard(): Boolean {
    val settings: RunnerAndConfigurationSettings? = environment.runnerAndConfigurationSettings
    val configuration = settings?.getConfiguration() ?: return false
    return RunDashboardManager.getInstance(configuration.getProject()).isShowInDashboard(configuration)
  }

  override fun getRunProfileName(): @NlsSafe String {
    return environment.runProfile.name
  }

  override fun getIcon(): Icon {
    return environment.executor.icon
  }

  override fun getRerunIcon(): Icon {
    return environment.executor.rerunIcon
  }

  override fun getRunnerAndConfigurationSettingsProxy(): RunnerAndConfigurationSettingsProxy? {
    val runnerAndConfigurationSettings = environment.runnerAndConfigurationSettings ?: return null
    return BackendRunnerAndConfigurationSettingsProxy(runnerAndConfigurationSettings)
  }

  override fun getContentToReuseProxy(): RunContentDescriptorProxy? {
    return environment.contentToReuse?.let { BackendRunContentDescriptorProxy(it) }
  }

  override fun isStarting(): Boolean {
    return ExecutionManager.getInstance(environment.project).isStarting(environment)
  }

  override fun performRestart() {
    ExecutionUtil.restart(environment)
  }
}