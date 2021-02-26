// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.task.impl

import com.intellij.execution.ExecutionTarget
import com.intellij.execution.Executor
import com.intellij.execution.RunnerAndConfigurationSettings
import com.intellij.execution.configurations.*
import com.intellij.execution.impl.ExecutionManagerImpl.Companion.setDelegatedRunProfile
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.execution.runners.ExecutionEnvironmentProvider
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.project.Project
import com.intellij.task.ExecuteRunConfigurationTask
import com.intellij.task.ProjectTaskRunner

/**
 * @author Vladislav.Soroka
 */
class ExecutionEnvironmentProviderImpl : ExecutionEnvironmentProvider {
  override fun createExecutionEnvironment(project: Project,
                                          runProfile: RunProfile,
                                          executor: Executor,
                                          target: ExecutionTarget,
                                          runnerSettings: RunnerSettings?,
                                          configurationSettings: ConfigurationPerRunnerSettings?,
                                          settings: RunnerAndConfigurationSettings?): ExecutionEnvironment? {
    val runTask: ExecuteRunConfigurationTask =
      ExecuteRunConfigurationTaskImpl(runProfile, target, runnerSettings, configurationSettings, settings)
    val environment = ProjectTaskRunner.EP_NAME.computeSafeIfAny {
      try {
        if (it.canRun(project, runTask)) {
          return@computeSafeIfAny it.createExecutionEnvironment(project, runTask, executor)
        }
      }
      catch (e: ProcessCanceledException) {
        throw e
      }
      catch (e: Exception) {
        LOG.error("Broken project task runner: " + it.javaClass.name, e)
      }
      null
    } ?: return null
    val environmentRunProfile = environment.runProfile
    setDelegatedRunProfile(environmentRunProfile, runProfile)
    copySettings(settings, environment)
    copyCommonRunProfileOptions(runProfile, environmentRunProfile)
    return environment
  }

  companion object {
    private val LOG = logger<ExecutionEnvironmentProvider>()

    private fun copySettings(settings: RunnerAndConfigurationSettings?, environment: ExecutionEnvironment) {
      if (settings == null) return
      val environmentSettings = environment.runnerAndConfigurationSettings
      if (environmentSettings != null && environmentSettings !== settings) {
        environmentSettings.isActivateToolWindowBeforeRun = settings.isActivateToolWindowBeforeRun
        environmentSettings.isEditBeforeRun = settings.isEditBeforeRun
      }
    }

    private fun copyCommonRunProfileOptions(runProfile: RunProfile, environmentRunProfile: RunProfile) {
      if (environmentRunProfile is RunConfigurationBase<*> && runProfile is RunConfigurationBase<*>) {
        val options = runProfile.state as? RunConfigurationOptions ?: return
        val environmentOption = environmentRunProfile.state as? RunConfigurationOptions ?: return
        environmentOption.apply {
          fileOutput.copyFrom(options.fileOutput)
          predefinedLogFiles.addAll(options.predefinedLogFiles)
          isShowConsoleOnStdOut = options.isShowConsoleOnStdOut
          isShowConsoleOnStdErr = options.isShowConsoleOnStdErr
          logFiles.addAll(options.logFiles)
          isAllowRunningInParallel = options.isAllowRunningInParallel
          remoteTarget = options.remoteTarget
          projectPathOnTarget = options.projectPathOnTarget
          selectedOptions.addAll(options.selectedOptions)
        }
      }
    }
  }
}