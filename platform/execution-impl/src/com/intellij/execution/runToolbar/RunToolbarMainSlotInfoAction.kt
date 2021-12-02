// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.runToolbar

import com.intellij.execution.runToolbar.components.MouseListenerHelper
import com.intellij.execution.runToolbar.components.ProcessesByType
import com.intellij.execution.runners.ExecutionEnvironment
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
import net.miginfocom.swing.MigLayout
import java.awt.Color
import java.beans.PropertyChangeEvent
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.UIManager

class RunToolbarMainSlotInfoAction : SegmentedCustomAction(), RTRunConfiguration {
  companion object {
    private val LOG = Logger.getInstance(RunToolbarMainSlotInfoAction::class.java)
    private val PROP_ACTIVE_PROCESS_COLOR = Key<Color>("ACTIVE_PROCESS_COLOR")
    private val PROP_ACTIVE_PROCESSES_UPDATED = Key<Boolean>("PROP_ACTIVE_PROCESSES_UPDATED")
    private val PROP_ACTIVE_PROCESSES = Key<MutableMap<RunToolbarProcess, MutableList<ExecutionEnvironment>>>("PROP_ACTIVE_PROCESSES")
  }

  override fun getRightSideType(): RTBarAction.Type = RTBarAction.Type.FLEXIBLE

  override fun actionPerformed(e: AnActionEvent) {

  }

  override fun checkMainSlotVisibility(state: RunToolbarMainSlotState): Boolean {
    return state == RunToolbarMainSlotState.INFO
  }

  override fun update(e: AnActionEvent) {
    e.presentation.isVisible = e.project?.let { project ->
      val manager = RunToolbarSlotManager.getInstance(project)
      val activeProcesses = manager.activeProcesses.processes

      manager.getMainOrFirstActiveProcess()?.let {
        e.presentation.putClientProperty(PROP_ACTIVE_PROCESS_COLOR, it.pillColor)
      }

      e.presentation.putClientProperty(RunToolbarMainSlotActive.ARROW_DATA, e.arrowIcon())
      val value = e.presentation.getClientProperty(PROP_ACTIVE_PROCESSES_UPDATED) ?: false
      e.presentation.putClientProperty(PROP_ACTIVE_PROCESSES_UPDATED, !value)
      e.presentation.putClientProperty(PROP_ACTIVE_PROCESSES, activeProcesses)

      activeProcesses.isNotEmpty()
    } ?: false

    if (!RunToolbarProcess.isExperimentalUpdatingEnabled) {
      e.mainState()?.let {
        e.presentation.isVisible = e.presentation.isVisible && checkMainSlotVisibility(it)
      }
    }
    traceLog(LOG, e)
  }


  override fun createCustomComponent(presentation: Presentation, place: String): SegmentedCustomPanel {
    return RunToolbarMainSlotInfo(presentation)
  }

  private class RunToolbarMainSlotInfo(presentation: Presentation) : SegmentedCustomPanel(presentation), PopupControllerComponent {
    private val arrow = JLabel()
    private val migLayout = MigLayout("fill, hidemode 3, ins 0, novisualpadding, ay center, flowx, gapx 0")

    private val info = JPanel(migLayout)
    private val processesComponents = mutableListOf<ProcessesByType>()

    init {
      layout = MigLayout("ins 0, fill, ay center")
      add(JPanel(MigLayout("ins 0, fill, novisualpadding, ay center, gap 0", "[pref!][min!]5[fill]5")).apply {
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

        add(info, "pushx, ay center, wmin 0")
        isOpaque = false

      }, "growx, wmin 10")

      info.isOpaque = false
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
      updateState()
    }

    private fun updateState() {
      updateArrow()

      presentation.getClientProperty(PROP_ACTIVE_PROCESS_COLOR)?.let {
        background = it
      } ?: kotlin.run {
        isOpaque = false
      }

      updateActiveProcesses()
    }

    private fun updateActiveProcesses() {
      presentation.getClientProperty(PROP_ACTIVE_PROCESSES)?.let {
        if (it.isEmpty()) return

        info.removeAll()

        var colConstr = ""

        while (processesComponents.size < it.size) {
          processesComponents.add(ProcessesByType())
        }

        var i = 0
        var showCount = false
        for (entry in it) {
          showCount = showCount || entry.value.size > 1 || it.size > 1
          val processesByType = processesComponents[i]
          processesByType.update(entry.key, entry.value, showCount)
          info.add(processesByType, "wmin 0")
          colConstr += if (showCount) "[pref!]" else "[fill]"
          i++
        }
        if (showCount) colConstr += "push"
        migLayout.columnConstraints = colConstr
      }

      info.revalidate()
      info.repaint()
    }

    private fun updateArrow() {
      presentation.getClientProperty(RunToolbarMainSlotActive.ARROW_DATA)?.let {
        arrow.icon = it
        toolTipText = ActionsBundle.message("action.RunToolbarShowHidePopupAction.click.to.show.popup.text")
      } ?: run {
        arrow.icon = null
        toolTipText = null
      }
    }
  }
}
