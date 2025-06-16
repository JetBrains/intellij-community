// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.runners

import com.intellij.execution.RunnerAndConfigurationSettings
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
interface RunnerAndConfigurationSettingsProxy {
  fun isDumbAware(): Boolean
}

internal class BackendRunnerAndConfigurationSettingsProxy(private val runnerAndConfigurationSettings: RunnerAndConfigurationSettings) : RunnerAndConfigurationSettingsProxy {
  override fun isDumbAware(): Boolean {
    return runnerAndConfigurationSettings.type.isDumbAware
  }
}