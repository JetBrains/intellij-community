// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.runToolbar

import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.actionSystem.Presentation
import com.intellij.openapi.actionSystem.ex.ComboBoxAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import com.intellij.openapi.wm.IdeFrame
import com.intellij.util.ui.JBUI
import java.awt.Color
import java.awt.Dimension
import java.beans.PropertyChangeEvent
import javax.swing.JComponent
import javax.swing.SwingUtilities

class RunToolbarMainMultipleProcessStartedAction : ComboBoxAction(), RTRunConfiguration {
  companion object {
    private val PROP_ACTIVE_PROCESS_COLOR = Key<Color>("ACTIVE_PROCESS_COLOR")
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

      manager.getMainOrFirstActiveProcess()?.let{
        e.presentation.putClientProperty(PROP_ACTIVE_PROCESS_COLOR, it.pillColor)
      }

      activeProcesses.getText()?.let {
        e.presentation.setText(it, false)
        true
      } ?: false
    } ?: false
  }

  override fun createComboBoxButton(presentation: Presentation): ComboBoxButton {
    return object : ComboBoxButton(presentation) {
      private var project: Project? = null

      override fun addNotify() {
        super.addNotify()
        project = (SwingUtilities.getWindowAncestor(this) as? IdeFrame)?.project
      }

      override fun showPopup() {

      }

      override fun isArrowVisible(presentation: Presentation): Boolean {
        return false
      }

      override fun presentationChanged(event: PropertyChangeEvent?) {
        presentation.getClientProperty(PROP_ACTIVE_PROCESS_COLOR)?.let {
          putClientProperty("JButton.backgroundColor", it)
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