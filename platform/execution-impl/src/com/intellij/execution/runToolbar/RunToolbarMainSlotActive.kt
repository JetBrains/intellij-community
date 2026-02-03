// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.runToolbar

import com.intellij.execution.runToolbar.RunToolbarProcessStartedAction.Companion.PROP_ACTIVE_ENVIRONMENT
import com.intellij.execution.runToolbar.components.MouseListenerHelper
import com.intellij.execution.runToolbar.components.TrimmedMiddleLabel
import com.intellij.icons.AllIcons
import com.intellij.idea.ActionsBundle
import com.intellij.openapi.actionSystem.ActionToolbar
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.Presentation
import com.intellij.openapi.actionSystem.impl.segmentedActionBar.SegmentedCustomAction
import com.intellij.openapi.actionSystem.impl.segmentedActionBar.SegmentedCustomPanel
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.util.Key
import com.intellij.util.ui.JBDimension
import com.intellij.util.ui.UIUtil
import net.miginfocom.swing.MigLayout
import java.awt.Color
import java.awt.Dimension
import java.awt.Font
import java.beans.PropertyChangeEvent
import javax.swing.Icon
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.UIManager

internal class RunToolbarMainSlotActive : SegmentedCustomAction(),
                                          RTBarAction {

  companion object {
    private val LOG = Logger.getInstance(RunToolbarMainSlotActive::class.java)
    val ARROW_DATA = Key<Icon?>("ARROW_DATA")
  }

  override fun actionPerformed(e: AnActionEvent) {
  }

  override fun checkMainSlotVisibility(state: RunToolbarMainSlotState): Boolean {
    return state == RunToolbarMainSlotState.PROCESS
  }

  override fun update(e: AnActionEvent) {
    RunToolbarProcessStartedAction.updatePresentation(e)

    val presentation = e.presentation
    if (!RunToolbarProcess.isExperimentalUpdatingEnabled) {
      e.mainState()?.let {
        presentation.isEnabledAndVisible = presentation.isEnabledAndVisible && checkMainSlotVisibility(it)
      }
    }
    presentation.isEnabled = presentation.isEnabled && e.isFromActionToolbar

    presentation.description = e.runToolbarData()?.let {
      RunToolbarData.prepareDescription(presentation.text,
                                        ActionsBundle.message("action.RunToolbarShowHidePopupAction.click.to.show.popup.text"))
    }

    presentation.putClientProperty(ARROW_DATA, e.arrowIcon())
    traceLog(LOG, e)
  }

  override fun createCustomComponent(presentation: Presentation, place: String): SegmentedCustomPanel {
    return RunToolbarMainSlotActive(presentation)
  }

  private class RunToolbarMainSlotActive(presentation: Presentation) : SegmentedCustomPanel(presentation), PopupControllerComponent {
    private val arrow = JLabel()
    private val dragArea = RunWidgetResizePane()

    private val setting = object : TrimmedMiddleLabel() {
      override fun getFont(): Font {
        return UIUtil.getToolbarFont()
      }

      override fun getForeground(): Color {
        return UIUtil.getLabelForeground()
      }
    }

    private val process = object : JLabel() {
      override fun getFont(): Font {
        return UIUtil.getToolbarFont()
      }

      override fun getForeground(): Color {
        return UIUtil.getLabelInfoForeground()
      }
    }

    init {
      layout = MigLayout("ins 0 0 0 3, fill, ay center, gap 0")
      val pane = JPanel().apply {
        layout = MigLayout("ins 0, fill, novisualpadding, ay center, gap 0", "[pref!][min!]3[shp 1, push]3[]push")

        add(JPanel().apply {
          isOpaque = false
          add(arrow)
          val d = preferredSize
          getProject()?.let {
            d.width = RunWidgetWidthHelper.getInstance(it).arrow
          }

          preferredSize = d
        })
        add(JPanel().apply {
          preferredSize = JBDimension(1, 12)
          minimumSize = JBDimension(1, 12)

          background = UIManager.getColor("Separator.separatorColor")
        })
        add(setting, "wmin 10")
        add(process, "wmin 0")
        isOpaque = false
      }
      add(dragArea, "pos 0 0")
      add(pane, "growx")

      MouseListenerHelper.addListener(pane, { doClick() }, { doShiftClick() }, { doRightClick() })
    }

    fun doRightClick() {
      RunToolbarRunConfigurationsAction.doRightClick(ActionToolbar.getDataContextFor(this))
    }

    private fun doClick() {
      val list = mutableListOf<PopupControllerComponentListener>()
      list.addAll(listeners)
      list.forEach { it.actionPerformedHandler() }
    }

    private fun doShiftClick() {
      ActionToolbar.getDataContextFor(this).editConfiguration()
    }

    private val listeners = mutableListOf<PopupControllerComponentListener>()

    override fun addListener(listener: PopupControllerComponentListener) {
      listeners.add(listener)
    }

    override fun removeListener(listener: PopupControllerComponentListener) {
      listeners.remove(listener)
    }

    override fun updateIconImmediately(isOpened: Boolean) {
      arrow.icon = if (isOpened) AllIcons.Toolbar.Collapse
      else AllIcons.Toolbar.Expand
    }

    override fun presentationChanged(event: PropertyChangeEvent) {
      updateArrow()
      updateEnvironment()
      setting.icon = presentation.icon
      setting.text = presentation.text
      toolTipText = presentation.description
    }

    private fun updateEnvironment() {
      presentation.getClientProperty(PROP_ACTIVE_ENVIRONMENT)?.let { env ->
        env.getRunToolbarProcess()?.let {
          background = it.pillColor
          process.text = if (env.isProcessTerminating()) ActionsBundle.message("action.RunToolbarRemoveSlotAction.terminating") else it.name
        }
      } ?: kotlin.run {
        isOpaque = false
      }
    }

    private fun updateArrow() {
      arrow.icon = presentation.getClientProperty(ARROW_DATA)
    }

    override fun getPreferredSize(): Dimension {
      val d = super.getPreferredSize()
      getProject()?.let {
        d.width = RunWidgetWidthHelper.getInstance(it).configWithArrow
      }
      return d
    }
  }
}