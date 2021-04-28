// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.externalSystem.service.execution.configuration

import com.intellij.execution.Executor
import com.intellij.execution.configuration.RunConfigurationExtensionBase
import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.configurations.RunnerSettings
import com.intellij.execution.configurations.SimpleJavaParameters
import com.intellij.execution.ui.BeforeRunComponent
import com.intellij.execution.ui.BeforeRunFragment
import com.intellij.execution.ui.RunConfigurationEditorFragment
import com.intellij.execution.ui.SettingsEditorFragment
import com.intellij.openapi.externalSystem.service.execution.ExternalSystemBeforeRunTask
import com.intellij.openapi.externalSystem.service.execution.ExternalSystemRunConfiguration
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.Key
import org.jetbrains.annotations.Nls

abstract class ExternalSystemRunConfigurationExtension : RunConfigurationExtensionBase<ExternalSystemRunConfiguration>() {
  override fun isApplicableFor(configuration: ExternalSystemRunConfiguration): Boolean {
    return true
  }

  override fun isEnabledFor(applicableConfiguration: ExternalSystemRunConfiguration, runnerSettings: RunnerSettings?): Boolean {
    return true
  }

  override fun patchCommandLine(configuration: ExternalSystemRunConfiguration,
                                runnerSettings: RunnerSettings?,
                                cmdLine: GeneralCommandLine,
                                runnerId: String) {
  }

  open fun updateVMParameters(
    configuration: ExternalSystemRunConfiguration,
    javaParameters: SimpleJavaParameters,
    runnerSettings: RunnerSettings?,
    executor: Executor) {
  }

  companion object {
    fun <C : ExternalSystemRunConfiguration, K : ExternalSystemBeforeRunTask> createBeforeRun(buildTaskKey: Key<K>): BeforeRunFragment<C> {
      val parentDisposable = Disposer.newDisposable()
      val beforeRunComponent = BeforeRunComponent(parentDisposable)
      val beforeRunFragment = BeforeRunFragment.createBeforeRun<C>(beforeRunComponent, buildTaskKey)
      Disposer.register(beforeRunFragment, parentDisposable)
      return beforeRunFragment
    }

    @Suppress("UNCHECKED_CAST")
    fun <C : ExternalSystemRunConfiguration> createSettingsTag(
      id: String,
      @Nls name: String,
      @Nls group: String,
      @Nls hint: String?,
      getter: (C) -> Boolean,
      setter: (C, Boolean) -> Unit,
      menuPosition: Int
    ): SettingsEditorFragment<C, *> =
      RunConfigurationEditorFragment.createSettingsTag<C>(
        id,
        name,
        group,
        { getter(it.configuration as C) },
        { it, v -> setter(it.configuration as C, v) },
        menuPosition
      ).apply {
        actionHint = hint
      }
  }
}