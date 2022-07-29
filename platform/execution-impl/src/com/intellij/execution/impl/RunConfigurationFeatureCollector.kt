// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.impl

import com.intellij.execution.RunManager
import com.intellij.execution.configurations.ConfigurationType
import com.intellij.ide.plugins.PluginFeatureService
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectPostStartupActivity

internal class RunConfigurationFeatureCollector : ProjectPostStartupActivity {
  override suspend fun execute(project: Project) {
    PluginFeatureService.instance.collectFeatureMapping(
      RunManager.CONFIGURATION_TYPE_FEATURE_ID,
      ConfigurationType.CONFIGURATION_TYPE_EP,
      ConfigurationType::getId,
      ConfigurationType::getDisplayName,
    )
  }
}