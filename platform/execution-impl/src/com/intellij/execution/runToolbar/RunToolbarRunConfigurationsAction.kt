// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.runToolbar

import com.intellij.execution.*
import com.intellij.execution.actions.RunConfigurationsComboBoxAction
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.ui.scale.JBUIScale
import com.intellij.util.ui.JBUI
import java.awt.Dimension
import java.awt.Insets
import javax.swing.JComponent
import javax.swing.SwingUtilities

open class RunToolbarRunConfigurationsAction : RunConfigurationsComboBoxAction(), RTRunConfiguration {
 companion object {

   fun doRightClick(dataContext: DataContext) {
     ActionManager.getInstance().getAction("RunToolbarSlotContextMenuGroup")?.let {
       if(it is ActionGroup) {
         SwingUtilities.invokeLater {
           val popup = JBPopupFactory.getInstance().createActionGroupPopup(
             null, it, dataContext, false, false, false, null, 5, null)

           popup.showInBestPositionFor(dataContext)
         }
       }
     }
   }
 }

  override fun addEditRunConfigurationItem(): Boolean {
    return false
  }

  override fun createFinalAction(configuration: RunnerAndConfigurationSettings, project: Project): AnAction {
    return RunToolbarSelectConfigAction(configuration, project)
  }

  override fun getSelectedConfiguration(e: AnActionEvent): RunnerAndConfigurationSettings? {
    return e.configuration() ?: e.project?.let {
      val value = RunManager.getInstance(it).selectedConfiguration
      e.setConfiguration(value)
      value
    }
  }

  override fun checkMainSlotVisibility(state: RunToolbarMainSlotState): Boolean {
    return false
  }

  override fun update(e: AnActionEvent) {
    super.update(e)
    e.presentation.isVisible = e.project?.let {
      !e.isActiveProcess() && e.presentation.isVisible
    } ?: false

    if (!RunToolbarProcess.experimentalUpdating()) {
      e.mainState()?.let {
        e.presentation.isVisible = e.presentation.isVisible && checkMainSlotVisibility(it)
      }
    }
  }

  override fun createCustomComponent(presentation: Presentation, place: String): JComponent {
    return object : RunConfigurationsComboBoxButton(presentation) {


      override fun getPreferredSize(): Dimension? {
        val d = super.getPreferredSize()
        d.width = FixWidthSegmentedActionToolbarComponent.RUN_CONFIG_WIDTH
        return d
      }

      override fun getArrowGap(): Int {
        return JBUIScale.scale(5)
      }

      override fun getMargin(): Insets {
        return JBUI.insets(0, 8, 0, 8)
      }

      override fun doRightClick() {
        doRightClick(dataContext)
      }

      override fun doShiftClick() {
        dataContext.editConfiguration()
        doClick()
      }
    }
  }

  private class RunToolbarSelectConfigAction(val configuration: RunnerAndConfigurationSettings,
                                             val project: Project) : DumbAwareAction() {
    init {

      var name = Executor.shortenNameIfNeeded(configuration.name)
      if (name.isEmpty()) {
        name = " "
      }
      val presentation = templatePresentation
      presentation.setText(name, false)
      presentation.description = ExecutionBundle.message("select.0.1", configuration.type.configurationTypeDescription, name)
      updateIcon(presentation)
    }

    private fun updateIcon(presentation: Presentation) {
      setConfigurationIcon(presentation, configuration, project)
    }

    override fun actionPerformed(e: AnActionEvent) {
      e.project?.let {
        e.setConfiguration(configuration)
        RunToolbarSlotManager.getInstance(it).saveData()
        updatePresentation(ExecutionTargetManager.getActiveTarget(project),
                           configuration,
                           project,
                           e.presentation,
                           e.place)
      }
    }

    override fun update(e: AnActionEvent) {
      super.update(e)
      updateIcon(e.presentation)
    }
  }

}

