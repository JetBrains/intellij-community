// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.runToolbar

import com.intellij.execution.*
import com.intellij.execution.actions.RunConfigurationsComboBoxAction
import com.intellij.execution.impl.EditConfigurationsDialog
import com.intellij.execution.runToolbar.components.ComboBoxArrowComponent
import com.intellij.execution.runToolbar.components.MouseListenerHelper
import com.intellij.execution.runToolbar.components.TrimmedMiddleLabel
import com.intellij.ide.DataManager
import com.intellij.ide.HelpTooltip
import com.intellij.idea.ActionsBundle
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.actionSystem.ex.ActionUtil
import com.intellij.openapi.actionSystem.impl.ActionToolbarImpl.DO_NOT_ADD_CUSTOMIZATION_HANDLER
import com.intellij.openapi.actionSystem.impl.segmentedActionBar.SegmentedCustomPanel
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.popup.JBPopup
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.wm.IdeFocusManager
import com.intellij.ui.Gray
import com.intellij.ui.JBColor
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import net.miginfocom.swing.MigLayout
import java.awt.Dimension
import java.awt.Font
import java.beans.PropertyChangeEvent
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

  open fun trace(e: AnActionEvent, add: String? = null) {

  }

  override fun getEditRunConfigurationAction(): AnAction? {
    return ActionManager.getInstance().getAction(RunToolbarEditConfigurationAction.ACTION_ID)
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
    if(!e.presentation.isVisible) return

    e.presentation.isVisible = !e.isActiveProcess()

    if (!RunToolbarProcess.isExperimentalUpdatingEnabled) {
      e.mainState()?.let {
        e.presentation.isVisible = e.presentation.isVisible && checkMainSlotVisibility(it)
      }
    }
    e.presentation.description = e.runToolbarData()?.let {
      RunToolbarData.prepareDescription(e.presentation.text, ActionsBundle.message("action.RunToolbarShowHidePopupAction.click.to.open.combo.text"))
    }
  }

  override fun createCustomComponent(presentation: Presentation, place: String): JComponent {
    return object : SegmentedCustomPanel(presentation) {
      private val setting = object : TrimmedMiddleLabel() {
        override fun getFont(): Font {
          return UIUtil.getToolbarFont()
        }
      }

      private val arrow = ComboBoxArrowComponent().getView()

      init {
        MouseListenerHelper.addListener(this, { doClick() }, { doShiftClick() }, { doRightClick() })
        fill()
        putClientProperty(DO_NOT_ADD_CUSTOMIZATION_HANDLER, true)
        background = JBColor.namedColor("ComboBoxButton.background", Gray.xDF)
      }

      override fun presentationChanged(event: PropertyChangeEvent) {
        setting.icon = presentation.icon
        setting.text = presentation.text
        setting.putClientProperty(DO_NOT_ADD_CUSTOMIZATION_HANDLER, true)


        isEnabled = presentation.isEnabled
        setting.isEnabled = isEnabled
        arrow.isVisible = isEnabled

        toolTipText = presentation.description
      }

      private fun fill() {
        layout = MigLayout("ins 0 0 0 3, novisualpadding, gap 0, fill, hidemode 3", "4[][min!]")

        add(setting, "ay center, growx, wmin 10")
        add(arrow)

        setting.border = JBUI.Borders.empty()
      }

      private fun doRightClick() {
        RunToolbarRunConfigurationsAction.doRightClick(ActionToolbar.getDataContextFor(this))
      }

      private fun doClick() {
        IdeFocusManager.getGlobalInstance().doWhenFocusSettlesDown { showPopup() }
      }

      fun showPopup() {
        val popup: JBPopup = createPopup() {}

        if (Registry.`is`("ide.helptooltip.enabled")) {
          HelpTooltip.setMasterPopup(this, popup)
        }
        popup.showUnderneathOf(this)
      }

      private fun createPopup(onDispose: Runnable): JBPopup {
        return createActionPopup(ActionToolbar.getDataContextFor(this), this, onDispose)
      }

      private fun doShiftClick() {
        val context = DataManager.getInstance().getDataContext(this)
        val project = CommonDataKeys.PROJECT.getData(context)
        if (project != null && !ActionUtil.isDumbMode(project)) {
          EditConfigurationsDialog(project).show()
          return
        }
      }

      override fun getPreferredSize(): Dimension {
        val d = super.getPreferredSize()
        d.width = FixWidthSegmentedActionToolbarComponent.RUN_CONFIG_WIDTH
        return d
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
        e.runToolbarData()?.clear()
        e.setConfiguration(configuration)
        e.id()?.let {id ->
          RunToolbarSlotManager.getInstance(it).configurationChanged(id, configuration)
        }

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

