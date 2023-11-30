package com.intellij.execution.multilaunch.design.popups

import com.intellij.execution.ExecutionBundle
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.popup.*
import com.intellij.openapi.ui.popup.util.BaseListPopupStep
import com.intellij.util.Consumer
import com.intellij.util.ui.EmptyIcon
import com.intellij.execution.multilaunch.MultiLaunchConfiguration
import com.intellij.execution.multilaunch.execution.executables.Executable
import com.intellij.execution.multilaunch.execution.executables.TaskExecutableTemplate
import com.intellij.execution.multilaunch.design.dialogs.AddMultipleConfigurationsDialog
import com.intellij.execution.multilaunch.execution.executables.ExecutableFactory
import com.intellij.execution.multilaunch.execution.executables.impl.RunConfigurationExecutableManager
import javax.swing.Icon

@Service(Service.Level.PROJECT)
class ExecutableSelectionPopupFactory(private val project: Project) {
  companion object {
    fun getInstance(project: Project) = project.service<ExecutableSelectionPopupFactory>()
  }

  fun createPopup(configuration: MultiLaunchConfiguration, existingExecutables: List<Executable?>, allowMultiple: Boolean, onSelected: (List<Executable?>) -> Unit): ListPopup {
    val runConfigs = RunConfigurationExecutableManager.getInstance(project).listExecutables(configuration)
      .filter { it !in existingExecutables }

    val tasks = TaskExecutableTemplate.EP_NAME.extensionList
      .mapNotNull { ExecutableFactory.getInstance(project).create(configuration, it) }

    return JBPopupFactory
      .getInstance()
      .createListPopup(ExecutablePopupStep(project, configuration, runConfigs, existingExecutables, tasks, allowMultiple, onSelected))
  }

  class ExecutablePopupStep(
    private val project: Project,
    private val configuration: MultiLaunchConfiguration,
    private val runConfigs: List<Executable>,
    private val existingExecutables: List<Executable?>,
    tasks: List<Executable>,
    private val allowMultiple: Boolean,
    private val onSelected: (List<Executable?>) -> Unit
  ) : BaseListPopupStep<PopupItem>(null) {
    init {
      values.clear()
      values.add(PopupItem.Tasks(tasks))
      if (allowMultiple) {
        values.add(PopupItem.AddMultiple)
      }
      values.addAll(runConfigs.map { PopupItem.Executable(it) })
    }

    override fun getTextFor(value: PopupItem?) = when(value) {
      is PopupItem.Executable -> value.executable.name
      is PopupItem.Tasks -> ExecutionBundle.message("run.configurations.multilaunch.add.tasks.option")
      is PopupItem.AddMultiple -> ExecutionBundle.message("run.configurations.multilaunch.add.multiple.option")
      null -> ""
    }

    override fun getIconFor(value: PopupItem?): Icon = when(value) {
      is PopupItem.Executable -> value.executable.icon ?: EmptyIcon.ICON_16
      else -> EmptyIcon.ICON_16
    }

    override fun getSeparatorAbove(value: PopupItem?): ListSeparator? {
      val separator by lazy { ListSeparator(ExecutionBundle.message("run.configurations.multilaunch.separator.run.configurations")) }
      return when {
        allowMultiple && value is PopupItem.AddMultiple -> separator
        !allowMultiple && value is PopupItem.Executable && runConfigs.any() && value.executable == runConfigs.first() -> separator
        else -> null
      }
    }

    override fun hasSubstep(selectedValue: PopupItem?) = selectedValue is PopupItem.Tasks

    override fun onChosen(selectedValue: PopupItem?, finalChoice: Boolean): PopupStep<*>? =
      when (selectedValue) {
        is PopupItem.Tasks -> ExecutableGroupStep(selectedValue.executables.toMutableList(), onSelected)
        is PopupItem.AddMultiple -> doFinalStep {
          val dialog = AddMultipleConfigurationsDialog(project, configuration, existingExecutables)
          if (dialog.showAndGet()) {
            onSelected(dialog.selectedItems)
          }
        }
        is PopupItem.Executable -> {
          onSelected(listOf(selectedValue.executable))
          FINAL_CHOICE
        }
        null -> FINAL_CHOICE
      }

    private class ExecutableGroupStep(
      items: MutableList<Executable>,
      private val onSelected: Consumer<List<Executable?>>
    ) : BaseListPopupStep<Executable>(null, items) {
      override fun getTextFor(value: Executable?) = value?.name ?: ""

      override fun getIconFor(value: Executable?): Icon = value?.icon ?: EmptyIcon.ICON_16

      override fun onChosen(selectedValue: Executable?, finalChoice: Boolean): PopupStep<*>? {
        onSelected.consume(listOf(selectedValue))
        return FINAL_CHOICE
      }
    }
  }

  sealed class PopupItem {
    class Executable(val executable: com.intellij.execution.multilaunch.execution.executables.Executable) : PopupItem()
    class Tasks(val executables: List<com.intellij.execution.multilaunch.execution.executables.Executable>) : PopupItem()
    data object AddMultiple : PopupItem()
  }
}