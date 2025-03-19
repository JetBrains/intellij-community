package com.intellij.execution.multilaunch.servicesView

import com.intellij.execution.ExecutionBundle
import com.intellij.execution.services.ServiceViewDescriptor
import com.intellij.icons.AllIcons
import com.intellij.ide.projectView.PresentationData
import com.intellij.navigation.ItemPresentation
import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.util.NlsContexts
import com.intellij.ui.AnimatedIcon
import com.intellij.ui.SimpleTextAttributes
import com.intellij.ui.components.JBPanelWithEmptyText
import com.intellij.execution.multilaunch.execution.ExecutableExecutionModel
import com.intellij.execution.multilaunch.execution.ExecutionStatus
import com.intellij.execution.multilaunch.execution.MultiLaunchExecutionModel
import com.intellij.execution.multilaunch.servicesView.actions.executable.CancelExecutableAction
import javax.swing.Icon
import javax.swing.JComponent

internal class ExecutableServiceViewDescriptor(
  private val configurationModel: MultiLaunchExecutionModel,
  private val executableModel: ExecutableExecutionModel,
) : ServiceViewDescriptor {
  private val toolbarActions by lazy {
    DefaultActionGroup(
      CancelExecutableAction(configurationModel, executableModel)
    )
  }

  override fun getPresentation(): ItemPresentation {
    return PresentationData().apply {
      setIcon(getStatusIcon(executableModel))
      addText(executableModel.descriptor.executable.name, SimpleTextAttributes.REGULAR_ATTRIBUTES)
      addText(" - ", SimpleTextAttributes.GRAY_ATTRIBUTES)
      addText(getStatusText(executableModel), SimpleTextAttributes.GRAY_ATTRIBUTES)
    }
  }

  override fun getToolbarActions(): ActionGroup {
    return toolbarActions
  }

  override fun getContentComponent(): JComponent {
    return JBPanelWithEmptyText().withEmptyText(ExecutionBundle.message("run.configurations.multilaunch.services.executable.use.toolbar.placeholder"))
  }

  override fun onNodeSelected(selectedServices: MutableList<Any>?) {
    super.onNodeSelected(selectedServices)
  }

  companion object {
    private fun getStatusIcon(state: ExecutableExecutionModel): Icon {
      return when (state.status.value) {
        is ExecutionStatus.NotStarted -> AllIcons.RunConfigurations.TestUnknown
        is ExecutionStatus.Waiting -> AnimatedIcon.Default.INSTANCE
        is ExecutionStatus.Started -> AnimatedIcon.Default.INSTANCE
        is ExecutionStatus.Finished -> AllIcons.RunConfigurations.TestPassed
        is ExecutionStatus.Failed -> AllIcons.RunConfigurations.TestFailed
        is ExecutionStatus.Canceled -> AllIcons.RunConfigurations.TestTerminated
      }
    }

    @NlsContexts.Label
    private fun getStatusText(state: ExecutableExecutionModel): String {
      return when (state.status.value) {
        is ExecutionStatus.NotStarted -> ExecutionBundle.message("run.configurations.multilaunch.status.not.started")
        is ExecutionStatus.Waiting -> ExecutionBundle.message("run.configurations.multilaunch.status.waiting", state.descriptor.condition.text)
        is ExecutionStatus.Started -> ExecutionBundle.message("run.configurations.multilaunch.status.started")
        is ExecutionStatus.Canceled -> ExecutionBundle.message("run.configurations.multilaunch.status.canceled")
        is ExecutionStatus.Failed -> ExecutionBundle.message("run.configurations.multilaunch.status.failed")
        is ExecutionStatus.Finished -> ExecutionBundle.message("run.configurations.multilaunch.status.finished")
      }
    }
  }
}