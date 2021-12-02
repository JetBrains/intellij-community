// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
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
import java.awt.Dimension
import java.awt.Font
import java.beans.PropertyChangeEvent
import javax.swing.Icon
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.UIManager

class RunToolbarMainSlotActive : SegmentedCustomAction(), RTBarAction {
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

    if (!RunToolbarProcess.isExperimentalUpdatingEnabled) {
      e.mainState()?.let {
        e.presentation.isEnabledAndVisible = e.presentation.isEnabledAndVisible && checkMainSlotVisibility(it)
      }
    }

    val a = JPanel()
    MigLayout("ins 0, fill, gap 0", "[200]")
    a.add(JLabel(), "pushx")

    e.presentation.description = e.runToolbarData()?.let {
      RunToolbarData.prepareDescription(e.presentation.text, ActionsBundle.message("action.RunToolbarShowHidePopupAction.click.to.show.popup.text"))
    }

    e.presentation.putClientProperty(ARROW_DATA, e.arrowIcon())
    traceLog(LOG, e)
  }

  override fun createCustomComponent(presentation: Presentation, place: String): SegmentedCustomPanel {
    return RunToolbarMainSlotActive(presentation)
}

private class RunToolbarMainSlotActive(presentation: Presentation) : SegmentedCustomPanel(presentation), PopupControllerComponent {
  private val arrow = JLabel()

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
      layout = MigLayout("ins 0 0 0 3, fill, ay center")
      val pane = JPanel().apply {
        layout = MigLayout("ins 0, fill, novisualpadding, ay center, gap 0", "[pref!][min!]3[shp 1]3[]")
        add(JPanel().apply {
          isOpaque = false
          add(arrow)
          val d = preferredSize
          d.width = FixWidthSegmentedActionToolbarComponent.ARROW_WIDTH
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

      add(pane)
    MouseListenerHelper.addListener(this, { doClick() }, { doShiftClick() }, { doRightClick() })
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
          process.text = if(env.isProcessTerminating()) ActionsBundle.message("action.RunToolbarRemoveSlotAction.terminating") else it.name
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
      d.width = FixWidthSegmentedActionToolbarComponent.CONFIG_WITH_ARROW_WIDTH
      return d
    }
  }
}