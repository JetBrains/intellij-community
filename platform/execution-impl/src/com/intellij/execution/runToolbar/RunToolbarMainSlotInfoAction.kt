// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.runToolbar

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
import java.awt.Font
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.beans.PropertyChangeEvent
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.SwingUtilities
import javax.swing.UIManager

class RunToolbarMainSlotInfoAction : SegmentedCustomAction(), RTRunConfiguration {
  companion object {
    private val LOG = Logger.getInstance(RunToolbarMainSlotInfoAction::class.java)
    private val PROP_ACTIVE_PROCESS_COLOR = Key<Color>("ACTIVE_PROCESS_COLOR")
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


      val activeProcesses = manager.activeProcesses

      manager.getMainOrFirstActiveProcess()?.let {
        e.presentation.putClientProperty(PROP_ACTIVE_PROCESS_COLOR, it.pillColor)
      }

      e.presentation.putClientProperty(RunToolbarMainSlotActive.ARROW_DATA, e.arrowIcon())

      activeProcesses.getText()?.let {
        e.presentation.setText(it, false)
        true
      } ?: false
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

    private val setting = object : JLabel() {
      override fun getFont(): Font {
        return UIUtil.getToolbarFont()
      }
    }

    init {
      layout = MigLayout("ins 0, fill, novisualpadding, ay center, gap 0", "[pref!][min!]5[]")
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
      add(setting)

      addMouseListener(object : MouseAdapter() {
        override fun mousePressed(e: MouseEvent) {
          if (SwingUtilities.isLeftMouseButton(e)) {
            e.consume()
            if (e.isShiftDown) {
              doShiftClick()
            }
            else {
              doClick()
            }
          }
          else if (SwingUtilities.isRightMouseButton(e)) {
            doRightClick()
          }
        }
      })
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
      updateArrow()
      setting.icon = presentation.icon
      setting.text = presentation.text

      presentation.getClientProperty(PROP_ACTIVE_PROCESS_COLOR)?.let {
        background = it
      } ?: kotlin.run {
        isOpaque = false
      }
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
