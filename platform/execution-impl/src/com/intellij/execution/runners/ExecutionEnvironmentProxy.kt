// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.runners

import com.intellij.openapi.util.NlsSafe
import org.jetbrains.annotations.ApiStatus
import javax.swing.Icon

/**
 * This interface abstracts the functionality related to [ExecutionEnvironment],
 * so it can be implemented differently on frontend and backend sides.
 */
@ApiStatus.Internal
interface ExecutionEnvironmentProxy {
  fun isShowInDashboard(): Boolean

  fun getRunProfileName(): @NlsSafe String

  fun getIcon(): Icon

  fun getRerunIcon(): Icon

  fun getRunnerAndConfigurationSettingsProxy(): RunnerAndConfigurationSettingsProxy?

  fun getContentToReuseProxy(): RunContentDescriptorProxy?

  fun isStarting(): Boolean

  fun performRestart()
}