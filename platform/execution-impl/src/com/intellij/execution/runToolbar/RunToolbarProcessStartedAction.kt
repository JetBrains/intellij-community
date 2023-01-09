// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.runToolbar

import com.intellij.execution.runToolbar.components.MouseListenerHelper
import com.intellij.execution.runToolbar.components.TrimmedMiddleLabel
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.idea.ActionsBundle
import com.intellij.openapi.actionSystem.ActionToolbar
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.Presentation
import com.intellij.openapi.actionSystem.ex.ComboBoxAction
import com.intellij.openapi.actionSystem.impl.segmentedActionBar.SegmentedCustomPanel
import com.intellij.openapi.util.Key
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import net.miginfocom.swing.MigLayout
import java.awt.Dimension
import java.awt.Font
import java.beans.PropertyChangeEvent
import javax.swing.JComponent
import javax.swing.JLabel

internal class RunToolbarProcessStartedAction : ComboBoxAction(),
                                                RTRunConfiguration {

  companion object {
    val PROP_ACTIVE_ENVIRONMENT = Key<ExecutionEnvironment>("PROP_ACTIVE_ENVIRONMENT")

    fun updatePresentation(e: AnActionEvent) {
      val presentation = e.presentation
      presentation.isEnabledAndVisible = e.project?.let { project ->
        e.runToolbarData()?.let {
          it.environment?.let { environment ->
            presentation.putClientProperty(PROP_ACTIVE_ENVIRONMENT, environment)
            environment.contentToReuse?.let { contentDescriptor ->
              presentation.setText(contentDescriptor.displayName, false)
              presentation.icon = contentDescriptor.icon
            } ?: run {
              presentation.text = ""
              presentation.icon = null
            }
            presentation.description = RunToolbarData.prepareDescription(presentation.text,
                                                                         ActionsBundle.message(
                                                                           "action.RunToolbarShowHidePopupAction.click.to.open.toolwindow.text"))

            true
          } ?: false
        } ?: false
      } ?: false
    }
  }

  override fun actionPerformed(e: AnActionEvent) {

  }

  override fun checkMainSlotVisibility(state: RunToolbarMainSlotState): Boolean {
    return state == RunToolbarMainSlotState.PROCESS
  }

  override fun getActionUpdateThread() = ActionUpdateThread.BGT

  override fun update(e: AnActionEvent) {
    super.update(e)
    updatePresentation(e)

    if (!RunToolbarProcess.isExperimentalUpdatingEnabled) {
      e.mainState()?.let {
        val presentation = e.presentation
        presentation.isEnabledAndVisible = presentation.isEnabledAndVisible && checkMainSlotVisibility(it)
      }
    }
    e.presentation.isEnabled = e.presentation.isEnabled && e.isFromActionToolbar
  }

  override fun createCustomComponent(presentation: Presentation, place: String): JComponent {
    return object : SegmentedCustomPanel(presentation) {
      val PROP_ACTIVE_ENVIRONMENT = Key<ExecutionEnvironment>("PROP_ACTIVE_ENVIRONMENT")

      private val setting = object : TrimmedMiddleLabel() {
        override fun getFont(): Font {
          return UIUtil.getToolbarFont()
        }
      }

      private val process = object : JLabel() {
        override fun getFont(): Font {
          return UIUtil.getToolbarFont()
        }
      }.apply {
        foreground = UIUtil.getLabelInfoForeground()
      }

      init {
        MouseListenerHelper.addListener(this, { showPopup() }, { doShiftClick() }, { doRightClick() })

        fill()
      }

      override fun presentationChanged(event: PropertyChangeEvent) {
        setting.icon = presentation.icon
        setting.text = presentation.text

        presentation.getClientProperty(RunToolbarProcessStartedAction.PROP_ACTIVE_ENVIRONMENT)?.let { environment ->
          environment.getRunToolbarProcess()?.let {
            updatePresentation(it)
            if (environment.isProcessTerminating()) {
              process.text = ActionsBundle.message("action.RunToolbarRemoveSlotAction.terminating")
            }
            true
          }
        }
      }

      private fun updatePresentation(toolbarProcess: RunToolbarProcess) {
        setting.text = presentation.text
        setting.icon = presentation.icon

        isEnabled = true

        toolTipText = presentation.description
        process.text = toolbarProcess.name

        background = toolbarProcess.pillColor
      }

      private fun fill() {
        layout = MigLayout("ins 0 0 0 3, novisualpadding, gap 0, fill", "4[shp 1]3[grow]push")

        add(setting, "ay center, pushx, wmin 10")
        add(process, "ay center, pushx, wmin 0")

        setting.border = JBUI.Borders.empty()
        process.border = JBUI.Borders.empty()
      }

      private fun showPopup() {
        presentation.getClientProperty(PROP_ACTIVE_ENVIRONMENT)?.showToolWindowTab()
      }

      private fun doRightClick() {
        RunToolbarRunConfigurationsAction.doRightClick(ActionToolbar.getDataContextFor(this))
      }

      private fun doShiftClick() {
        ActionToolbar.getDataContextFor(this).editConfiguration()
      }

      override fun getPreferredSize(): Dimension {
        val d = super.getPreferredSize()
        getProject()?.let {
          d.width = RunWidgetWidthHelper.getInstance(it).runConfig
        }

        return d
      }
    }
  }
}