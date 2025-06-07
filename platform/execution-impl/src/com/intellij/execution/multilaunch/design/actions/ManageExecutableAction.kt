package com.intellij.execution.multilaunch.design.actions

import com.intellij.execution.multilaunch.MultiLaunchConfiguration
import com.intellij.execution.multilaunch.design.ExecutableRow
import com.intellij.execution.multilaunch.design.MultiLaunchConfigurationViewModel
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.actionSystem.impl.SimpleDataContext
import com.intellij.openapi.project.Project
import java.awt.Rectangle

internal val MULTILAUNCH_CONFIGURATION_KEY = DataKey.create<MultiLaunchConfiguration>("MULTILAUNCH_CONFIGURATION")
internal val MULTILAUNCH_POPUP_BOUNDS_KEY = DataKey.create<Rectangle>("MULTILAUNCH_POPUP_BOUNDS")
internal val MULTILAUNCH_CONTEXT_KEY = DataKey.create<ExecutableRow>("MULTILAUNCH_ROW")
internal val MULTILAUNCH_EXECUTABLE_VIEW_MODEL_KEY = DataKey.create<MultiLaunchConfigurationViewModel>("MULTILAUNCH_EXECUTABLE_VIEW_MODEL_KEY")

abstract class ManageExecutableAction : AnAction() {

  companion object {
    fun createContext(project: Project?, viewModel: MultiLaunchConfigurationViewModel?, executionContext: ExecutableRow?, bounds: Rectangle?): DataContext {
      return SimpleDataContext.builder()
        .add(CommonDataKeys.PROJECT, project)
        .add(MULTILAUNCH_CONFIGURATION_KEY, viewModel?.configuration)
        .add(MULTILAUNCH_POPUP_BOUNDS_KEY, bounds)
        .add(MULTILAUNCH_CONTEXT_KEY, executionContext)
        .add(MULTILAUNCH_EXECUTABLE_VIEW_MODEL_KEY, viewModel)
        .build()
    }
  }

  protected val AnActionEvent.configuration: MultiLaunchConfiguration? get() = dataContext.getData(MULTILAUNCH_CONFIGURATION_KEY)
  protected val AnActionEvent.popupBounds: Rectangle? get() = dataContext.getData(MULTILAUNCH_POPUP_BOUNDS_KEY)
  protected val AnActionEvent.editableRow: ExecutableRow? get() = dataContext.getData(MULTILAUNCH_CONTEXT_KEY)
  protected val AnActionEvent.executablesViewModel: MultiLaunchConfigurationViewModel? get() = dataContext.getData(MULTILAUNCH_EXECUTABLE_VIEW_MODEL_KEY)
}
