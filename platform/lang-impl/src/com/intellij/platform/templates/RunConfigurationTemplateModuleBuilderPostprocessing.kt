// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.templates

import com.intellij.execution.RunManager.Companion.getInstance
import com.intellij.execution.configurations.ModuleBasedConfiguration
import com.intellij.execution.impl.RunManagerImpl.Companion.getInstanceImpl
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager

internal class RunConfigurationTemplateModuleBuilderPostprocessing : TemplateModuleBuilderPostprocessing {
  override fun postProcessCreatedModule(module: Module) {
    val runManager = getInstance(module.getProject())
    for (configuration in runManager.allConfigurationsList) {
      if (configuration is ModuleBasedConfiguration<*, *>) {
        configuration.getConfigurationModule().module = module
      }
    }
  }

  override fun applyProjectDefaults(project: Project) {
    val defaultProject = ProjectManager.getInstance().getDefaultProject()
    val selectedConfiguration = getInstance(project).selectedConfiguration
    getInstanceImpl(defaultProject).copyTemplatesToProjectFromTemplate(project)
    getInstance(project).selectedConfiguration = selectedConfiguration
  }
}
