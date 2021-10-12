// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.runToolbar

import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.idea.ActionsBundle
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.actionSystem.Presentation
import com.intellij.openapi.actionSystem.ex.ComboBoxAction
import com.intellij.openapi.util.Key
import com.intellij.ui.JBColor
import com.intellij.util.ui.EmptyIcon
import com.intellij.util.ui.JBDimension
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import net.miginfocom.swing.MigLayout
import java.awt.Dimension
import java.awt.Font
import java.awt.Insets
import java.beans.PropertyChangeEvent
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JPanel

class RunToolbarProcessStartedAction : ComboBoxAction(), RTRunConfiguration {
  companion object {
    val PROP_ACTIVE_ENVIRONMENT = Key<ExecutionEnvironment>("PROP_ACTIVE_ENVIRONMENT")

    fun updatePresentation(e: AnActionEvent) {
      e.presentation.isEnabledAndVisible = e.project?.let { project ->
        e.runToolbarData()?.let {
          it.environment?.let { environment ->
            e.presentation.putClientProperty(PROP_ACTIVE_ENVIRONMENT, environment)
            environment.contentToReuse?.let { contentDescriptor ->
              e.presentation.setText(contentDescriptor.displayName, false)
              e.presentation.icon = contentDescriptor.icon
            } ?: run {
              e.presentation.text = ""
              e.presentation.icon = null
            }
            e.presentation.description = RunToolbarData.prepareDescription(e.presentation.text,
              ActionsBundle.message("action.RunToolbarShowHidePopupAction.click.to.open.toolwindow.text"))

            true
          } ?: false
        } ?: false
      } ?: false
    }
  }

  override fun createPopupActionGroup(button: JComponent?): DefaultActionGroup = DefaultActionGroup()

  override fun actionPerformed(e: AnActionEvent) {

  }

  override fun checkMainSlotVisibility(state: RunToolbarMainSlotState): Boolean {
    return state == RunToolbarMainSlotState.PROCESS
  }

  override fun update(e: AnActionEvent) {
    super.update(e)
    updatePresentation(e)

    if (!RunToolbarProcess.isExperimentalUpdatingEnabled) {
      e.mainState()?.let {
        e.presentation.isEnabledAndVisible = e.presentation.isEnabledAndVisible && checkMainSlotVisibility(it)
      }
    }
  }

  override fun createComboBoxButton(presentation: Presentation): ComboBoxButton {
    return object : ComboBoxButton(presentation) {

      override fun showPopup() {
        presentation.getClientProperty(PROP_ACTIVE_ENVIRONMENT)?.let { environment ->
          environment.showToolWindowTab()
        }
      }

      override fun doRightClick() {
        RunToolbarRunConfigurationsAction.doRightClick(dataContext)
      }

      override fun doShiftClick() {
        dataContext.editConfiguration()
      }

      override fun isArrowVisible(presentation: Presentation): Boolean {
        return false
      }

      override fun presentationChanged(event: PropertyChangeEvent?) {
        isVisible = presentation.getClientProperty(PROP_ACTIVE_ENVIRONMENT)?.let { environment ->
          environment.getRunToolbarProcess()?.let {
            updatePresentation(it)
            if(environment.isProcessTerminating()) {
              process.text = ActionsBundle.message("action.RunToolbarRemoveSlotAction.terminating")
            }
            true
          }

        } ?: false
      }

      private fun updatePresentation(it: RunToolbarProcess) {
        setting.text = presentation.text
        presentation.icon?.let {
          icon = it
          setting.icon = EmptyIcon.create(it.iconWidth).withIconPreScaled(true)
        } ?: run {
          setting.icon = null
          icon = null
        }

        isEnabled = true

        toolTipText = presentation.description
        process.text = it.name
        putClientProperty("JButton.backgroundColor", it.pillColor)
      }

      private val setting = object : JLabel() {
        override fun getFont(): Font? {
          return if (isSmallVariant) UIUtil.getToolbarFont() else UIUtil.getLabelFont()
        }
      }.apply {
        minimumSize = JBDimension(JBUI.scale(110), minHeight, true)
      }
      private val process = object : JLabel() {
        override fun getFont(): Font? {
          return if (isSmallVariant) UIUtil.getToolbarFont() else UIUtil.getLabelFont()
        }
      }.apply {
        foreground = JBColor.namedColor("infoPanelForeground", JBColor(0x808080, 0x8C8C8C))
        minimumSize = JBDimension(JBUI.scale(40), minHeight, true)
      }

      private val pane =  object : JPanel(){
        override fun getInsets(): Insets {
          return JBUI.insets(0, 0, 0, 3)
        }
      }.apply {
        layout = MigLayout("ins 0, fill, novisualpadding", "4[shp 1]3[]")

        add(setting, "ay center, pushx, wmin 10")
        add(process, "ay center, pushx, wmin 0")

       // setting.font = UIUtil.getToolbarFont()
        process.font = UIUtil.getToolbarFont()

        setting.border = JBUI.Borders.empty()
        process.border = JBUI.Borders.empty()

        isOpaque = false
      }

      init {
        layout = MigLayout("ins 0, fill")
        add(pane)
        text = null


        //  border = JBUI.Borders.empty()
      }

      override fun getPreferredSize(): Dimension {
        val d = super.getPreferredSize()
        d.width = FixWidthSegmentedActionToolbarComponent.RUN_CONFIG_WIDTH
        return d
      }


/*      override fun doShiftClick() {
        val context = DataManager.getInstance().getDataContext(this)
        val project = CommonDataKeys.PROJECT.getData(context)
        if (project != null && !ActionUtil.isDumbMode(project)) {
          EditConfigurationsDialog(project).show()
          return
        }
        super.doShiftClick()
      }*/
    }
  }


}