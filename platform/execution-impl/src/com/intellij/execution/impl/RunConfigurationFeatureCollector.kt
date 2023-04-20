// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.impl

import com.intellij.execution.RunManager
import com.intellij.execution.configurations.ConfigurationType
import com.intellij.ide.plugins.PluginFeatureService
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectPostStartupActivity
import kotlinx.coroutines.delay
import kotlin.time.Duration.Companion.minutes

private class RunConfigurationFeatureCollector : ProjectPostStartupActivity {
  override suspend fun execute(project: Project) {
    // no hurry to update current feature mapping of all run configurations types
    delay(10.minutes)

    PluginFeatureService.instance.collectFeatureMapping(
      featureType = RunManager.CONFIGURATION_TYPE_FEATURE_ID,
      ep = ConfigurationType.CONFIGURATION_TYPE_EP,
      idMapping = ConfigurationType::getId,
      displayNameMapping = ConfigurationType::getDisplayName,
    )
  }
}