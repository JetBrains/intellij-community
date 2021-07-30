// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.runToolbar

import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.actionSystem.Presentation
import com.intellij.openapi.actionSystem.ex.ComboBoxAction
import com.intellij.openapi.util.Key
import com.intellij.util.ui.JBUI
import java.awt.Dimension
import java.beans.PropertyChangeEvent
import javax.swing.JComponent

class RunToolbarMainMultipleProcessStartedAction : ComboBoxAction(), RTRunConfiguration {
  companion object {
    private val PROP_ACTIVE_PROCESS = Key<RunToolbarProcess>("ACTIVE_PROCESS")
  }

  override fun getRightSideType(): RTBarAction.Type = RTBarAction.Type.FLEXIBLE

  override fun createPopupActionGroup(button: JComponent?): DefaultActionGroup = DefaultActionGroup()

  override fun actionPerformed(e: AnActionEvent) {

  }

  override fun update(e: AnActionEvent) {
    e.presentation.isVisible = e.project?.let { project ->
      val manager = RunToolbarSlotManager.getInstance(project)
      val state = manager.getState()
      if(!e.isItRunToolbarMainSlot() || !state.isActive() || e.isOpened() || state.isSingleMain())  return@let false

      val activeProcesses = manager.activeProcesses
      activeProcesses.processes.keys.firstOrNull()?.let {
        e.presentation.putClientProperty(PROP_ACTIVE_PROCESS, it)
      }

      activeProcesses.getText()?.let {
        e.presentation.setText(it, false)
        true
      } ?: false
    } ?: false
  }

  override fun createComboBoxButton(presentation: Presentation): ComboBoxButton {
    return object : ComboBoxButton(presentation) {
      override fun showPopup() {

      }

      override fun isArrowVisible(presentation: Presentation): Boolean {
        return false
      }

      override fun presentationChanged(event: PropertyChangeEvent?) {
        presentation.getClientProperty(PROP_ACTIVE_PROCESS)?.let {
          putClientProperty("JButton.backgroundColor", it.pillColor)
        }

        super.presentationChanged(event)

        isEnabled = true
      }

      override fun getPreferredSize(): Dimension {
        return Dimension(JBUI.scale(180), super.getPreferredSize().height)
      }

    }
  }


}