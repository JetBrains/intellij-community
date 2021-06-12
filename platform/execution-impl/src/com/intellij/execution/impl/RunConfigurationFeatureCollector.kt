// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.impl

import com.intellij.execution.RunManager
import com.intellij.execution.configurations.ConfigurationType
import com.intellij.ide.plugins.PluginFeatureService
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.StartupActivity

class RunConfigurationFeatureCollector : StartupActivity.Background {
  override fun runActivity(project: Project) {
    PluginFeatureService.instance.collectFeatureMapping(
      RunManager.CONFIGURATION_TYPE_FEATURE_ID,
      ConfigurationType.CONFIGURATION_TYPE_EP,
      ConfigurationType::getId,
      ConfigurationType::getDisplayName,
    )
  }
}