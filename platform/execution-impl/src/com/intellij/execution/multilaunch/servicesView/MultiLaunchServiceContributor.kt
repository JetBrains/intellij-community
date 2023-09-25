package com.intellij.execution.multilaunch.servicesView

import com.intellij.execution.ExecutionBundle
import com.intellij.execution.services.ServiceViewContributor
import com.intellij.execution.services.ServiceViewDescriptor
import com.intellij.execution.services.SimpleServiceViewDescriptor
import com.intellij.openapi.project.Project
import com.intellij.ui.components.JBPanelWithEmptyText
import com.intellij.execution.multilaunch.execution.ExecutionModel
import com.intellij.util.ui.EmptyIcon
import javax.swing.JComponent

class MultiLaunchServiceContributor : ServiceViewContributor<ConfigurationServiceContributor> {
  override fun getViewDescriptor(project: Project): ServiceViewDescriptor {
    return ViewDescriptor()
  }

  override fun getServices(project: Project): MutableList<ConfigurationServiceContributor> {
    return ExecutionModel
      .getInstance(project)
      .configurations.values
      .map { ConfigurationServiceContributor(it) }
      .toMutableList()
  }

  override fun getServiceDescriptor(project: Project, service: ConfigurationServiceContributor): ServiceViewDescriptor {
    return service.getViewDescriptor(project)
  }

  private class ViewDescriptor : SimpleServiceViewDescriptor(
    ExecutionBundle.message("run.configurations.multilaunch.configuration.plural.name"), EmptyIcon.ICON_16 /* RiderIcons.RunConfigurations.MultiRun */) {
    override fun getContentComponent(): JComponent {
      return JBPanelWithEmptyText().withEmptyText(ExecutionBundle.message("run.configurations.multilaunch.services.select.multilaunch.placeholder"))
    }
  }
}

