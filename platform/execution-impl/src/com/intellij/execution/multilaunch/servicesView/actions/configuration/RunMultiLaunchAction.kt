package com.intellij.execution.multilaunch.servicesView.actions.configuration

import com.intellij.icons.AllIcons
import com.intellij.execution.multilaunch.MultiLaunchConfiguration
import com.intellij.execution.multilaunch.execution.ExecutionMode
import com.intellij.idea.ActionsBundle

@Suppress("ComponentNotRegistered")
class RunMultiLaunchAction(configuration: MultiLaunchConfiguration) : ExecuteMultiLaunchAction(configuration, ActionsBundle.message("action.multilaunch.RunMultiLaunchAction.text"), AllIcons.Actions.Execute, ExecutionMode.Run)