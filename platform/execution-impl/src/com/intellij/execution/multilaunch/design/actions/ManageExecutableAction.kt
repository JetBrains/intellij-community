package com.intellij.execution.multilaunch.design.actions

import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.actionSystem.impl.SimpleDataContext
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.NlsActions
import com.intellij.execution.multilaunch.MultiLaunchConfiguration
import com.intellij.execution.multilaunch.design.ExecutableRow
import com.intellij.execution.multilaunch.design.MultiLaunchConfigurationViewModel
import java.awt.Rectangle

abstract class ManageExecutableAction(@NlsActions.ActionText text: String) : AnAction(text) {
  companion object {
    val MULTILAUNCH_CONFIGURATION_KEY = DataKey.create<MultiLaunchConfiguration>("MULTILAUNCH_CONFIGURATION")
    val MULTILAUNCH_POPUP_BOUNDS_KEY = DataKey.create<Rectangle>("MULTILAUNCH_POPUP_BOUNDS")
    val MULTILAUNCH_CONTEXT_KEY = DataKey.create<ExecutableRow>("MULTILAUNCH_ROW")
    val MULTILAUNCH_EXECUTABLE_VIEW_MODEL_KEY = DataKey.create<MultiLaunchConfigurationViewModel>("MULTILAUNCH_EXECUTABLE_VIEW_MODEL_KEY")
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

  protected val AnActionEvent.configuration get() = dataContext.getData(MULTILAUNCH_CONFIGURATION_KEY)
  protected val AnActionEvent.popupBounds get() = dataContext.getData(MULTILAUNCH_POPUP_BOUNDS_KEY)
  protected val AnActionEvent.editableRow get() = dataContext.getData(MULTILAUNCH_CONTEXT_KEY)
  protected val AnActionEvent.executablesViewModel get() = dataContext.getData(MULTILAUNCH_EXECUTABLE_VIEW_MODEL_KEY)
}

