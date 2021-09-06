// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.runToolbar

import com.intellij.execution.runToolbar.RunToolbarProcessStartedAction.Companion.PROP_ACTIVE_ENVIRONMENT
import com.intellij.execution.runToolbar.RunToolbarProcessStartedAction.Companion.updatePresentation
import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.ActionToolbar
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.Presentation
import com.intellij.openapi.actionSystem.impl.segmentedActionBar.SegmentedCustomAction
import com.intellij.openapi.actionSystem.impl.segmentedActionBar.SegmentedCustomPanel
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.NlsActions
import com.intellij.ui.JBColor
import com.intellij.util.ui.JBDimension
import com.intellij.util.ui.UIUtil
import net.miginfocom.swing.MigLayout
import java.awt.Dimension
import java.awt.Font
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.beans.PropertyChangeEvent
import javax.swing.*

class RunToolbarMainSlotActive : SegmentedCustomAction(), RTBarAction {
  companion object {
     val ARROW_DATA = Key<Pair<Icon, @NlsActions.ActionText String>?>("ARROW_DATA")
  }

  override fun actionPerformed(e: AnActionEvent) {

  }

  override fun checkMainSlotVisibility(state: RunToolbarMainSlotState): Boolean {
    return state == RunToolbarMainSlotState.PROCESS
  }

  override fun update(e: AnActionEvent) {
    updatePresentation(e)

    if (!RunToolbarProcess.experimentalUpdating()) {
      e.mainState()?.let {
        e.presentation.isEnabledAndVisible = e.presentation.isEnabledAndVisible && checkMainSlotVisibility(it)
      }
    }

    val a = JPanel()
    MigLayout("ins 0, fill, gap 0", "[200]")
    a.add(JLabel(), "pushx")

    e.presentation.putClientProperty(ARROW_DATA, e.arrowData())
  }

  override fun createCustomComponent(presentation: Presentation, place: String): SegmentedCustomPanel {
    return RunToolbarMainSlotActive(presentation)
}

private class RunToolbarMainSlotActive(presentation: Presentation) : SegmentedCustomPanel(presentation), PopupControllerComponent {
    private val arrow = JLabel()

    private val setting = object : JLabel() {
      override fun getFont(): Font {
        return UIUtil.getToolbarFont()
      }
    }

    private val process = object : JLabel() {
      override fun getFont(): Font {
        return UIUtil.getToolbarFont()
      }
    }.apply {
      foreground = JBColor.namedColor("infoPanelForeground", JBColor(0x808080, 0x8C8C8C))
    }

    init {
      layout = MigLayout("ins 0, fill, novisualpadding, ay center, gap 0", "[pref!][min!]4[]3[]push")
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
      add(process)

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

    }

    private fun updateEnvironment() {
      presentation.getClientProperty(PROP_ACTIVE_ENVIRONMENT)?.getRunToolbarProcess()?.let {
        background = it.pillColor
        process.text = it.name
      } ?: kotlin.run {
        isOpaque = false
      }
    }

    private fun updateArrow() {
      presentation.getClientProperty(ARROW_DATA)?.let {
        arrow.icon = it.first
        toolTipText = it.second
      } ?: run {
        arrow.icon = null
        toolTipText = null
      }
    }

    override fun getPreferredSize(): Dimension {
      val d = super.getPreferredSize()
      d.width = FixWidthSegmentedActionToolbarComponent.CONFIG_WITH_ARROW_WIDTH
      return d
    }
  }
}