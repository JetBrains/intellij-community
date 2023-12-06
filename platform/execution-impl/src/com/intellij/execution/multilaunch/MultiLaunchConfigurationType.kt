package com.intellij.execution.multilaunch

import com.intellij.execution.ExecutionBundle
import com.intellij.execution.configurations.ConfigurationType
import com.intellij.icons.AllIcons

class MultiLaunchConfigurationType : ConfigurationType {
    override fun getDisplayName() = ExecutionBundle.message("run.configurations.multilaunch.configuration.name")

    override fun getConfigurationTypeDescription() = ExecutionBundle.message("run.configurations.multilaunch.configuration.description")

    override fun getIcon() = AllIcons.RunConfigurations.MultiLaunch

    override fun getId() = "com.intellij.execution.configurations.multilaunch"

    override fun getConfigurationFactories() = arrayOf(MultiLaunchConfigurationFactory(this))
}