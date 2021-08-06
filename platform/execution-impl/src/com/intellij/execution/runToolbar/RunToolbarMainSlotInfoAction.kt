// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.runToolbar

import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.Presentation
import com.intellij.openapi.actionSystem.impl.segmentedActionBar.SegmentedCustomAction
import com.intellij.openapi.actionSystem.impl.segmentedActionBar.SegmentedCustomPanel
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
    private val PROP_ACTIVE_PROCESS_COLOR = Key<Color>("ACTIVE_PROCESS_COLOR")
  }

  override fun getRightSideType(): RTBarAction.Type = RTBarAction.Type.FLEXIBLE

  override fun actionPerformed(e: AnActionEvent) {

  }

  override fun update(e: AnActionEvent) {
    e.presentation.isVisible = e.project?.let { project ->
      val manager = RunToolbarSlotManager.getInstance(project)
      val state = manager.getState()
      if(!e.isItRunToolbarMainSlot() || !state.isActive() || e.isOpened() || state.isSingleMain())  return@let false

      val activeProcesses = manager.activeProcesses

      manager.getMainOrFirstActiveProcess()?.let{
        e.presentation.putClientProperty(PROP_ACTIVE_PROCESS_COLOR, it.pillColor)
      }

      e.presentation.putClientProperty(RunToolbarMainSlotActive.ARROW_DATA, e.arrowData())

      activeProcesses.getText()?.let {
        e.presentation.setText(it, false)
        true
      } ?: false
    } ?: false
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
        }
      })
    }

    private fun doClick() {
      listeners.forEach { it.actionPerformedHandler() }
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
        arrow.icon = it.first
        toolTipText = it.second
      } ?: run {
        arrow.icon = null
        toolTipText = null
      }
    }
  }
}
