package com.intellij.execution.multilaunch

import com.intellij.execution.configurations.ConfigurationFactory
import com.intellij.execution.configurations.RunConfiguration
import com.intellij.execution.configurations.RunConfigurationSingletonPolicy
import com.intellij.openapi.project.Project
import com.intellij.execution.multilaunch.state.MultiLaunchConfigurationSnapshot

class MultiLaunchConfigurationFactory(configurationType: MultiLaunchConfigurationType) : ConfigurationFactory(configurationType) {
  override fun createTemplateConfiguration(project: Project): RunConfiguration {
    return MultiLaunchConfiguration(project, this, "MultiLaunch")
  }

  override fun getId() = "MultiLaunchConfiguration"

  override fun getOptionsClass() = MultiLaunchConfigurationSnapshot::class.java

  override fun getSingletonPolicy() = RunConfigurationSingletonPolicy.SINGLE_INSTANCE_ONLY
}