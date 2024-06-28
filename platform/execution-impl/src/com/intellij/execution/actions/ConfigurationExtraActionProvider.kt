// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.actions

import com.intellij.execution.RunnerAndConfigurationSettings
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.project.Project

interface ConfigurationExtraActionProvider {
  companion object {
    @JvmField
    val EXTENSION_NAME: ExtensionPointName<ConfigurationExtraActionProvider> =
      ExtensionPointName("com.intellij.configurationExtraActionProvider")
  }

  fun createAction(project: Project, configuration: RunnerAndConfigurationSettings): AnAction?
}