package com.intellij.execution.multilaunch.servicesView

import com.intellij.execution.ExecutionBundle
import com.intellij.execution.multilaunch.execution.ExecutableExecutionModel
import com.intellij.execution.multilaunch.execution.MultiLaunchExecutionModel
import com.intellij.execution.services.ServiceViewDescriptor
import com.intellij.execution.services.ServiceViewProvidingContributor
import com.intellij.execution.services.SimpleServiceViewDescriptor
import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.project.Project
import com.intellij.ui.components.JBPanelWithEmptyText
import com.intellij.execution.multilaunch.servicesView.actions.configuration.DebugMultiLaunchAction
import com.intellij.execution.multilaunch.servicesView.actions.configuration.RunMultiLaunchAction
import com.intellij.execution.multilaunch.servicesView.actions.configuration.StopMultiLaunchAction
import javax.swing.JComponent

class ConfigurationServiceContributor internal constructor(
  private val model: MultiLaunchExecutionModel
) : ServiceViewProvidingContributor<ExecutableExecutionModel, MultiLaunchExecutionModel> {
  override fun getViewDescriptor(project: Project) = object : SimpleServiceViewDescriptor(model.configuration.name, model.configuration.icon) {

    private val toolbarActions by lazy {
      DefaultActionGroup(
        RunMultiLaunchAction(model.settings),
        DebugMultiLaunchAction(model.settings),
        StopMultiLaunchAction(model)
      )
    }

    override fun getContentComponent(): JComponent {
      return JBPanelWithEmptyText().withEmptyText(ExecutionBundle.message("run.configurations.multilaunch.services.select.executable.placeholder"))
    }

    override fun getToolbarActions(): ActionGroup {
      return toolbarActions
    }
  }

  override fun getServices(project: Project): MutableList<ExecutableExecutionModel> {
    return model.executables.values.toMutableList()
  }

  override fun asService() = model

  override fun getServiceDescriptor(project: Project, service: ExecutableExecutionModel): ServiceViewDescriptor {
    return ExecutableServiceViewDescriptor(model, service)
  }
}