// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.runToolbar

import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.actionSystem.ex.CustomComponentAction
import com.intellij.openapi.actionSystem.impl.ActionButton
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogBuilder
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.util.Disposer
import javax.swing.JComponent

class RunToolbarExtraSlotsAction: AnAction(), CustomComponentAction, DumbAware {

  override fun actionPerformed(e: AnActionEvent) {

  }

  override fun update(e: AnActionEvent) {
    e.project?.let {
      val slotManager = RunToolbarSlotManager.getInstance(it)
      val isOpened = e.dataContext.getData(RunToolbarMainWidgetComponent.RUN_TOOLBAR_MAIN_WIDGET_COMPONENT_KEY)?.isOpened
                     ?: false
      e.presentation.icon = when {
        isOpened -> AllIcons.Toolbar.Collapse
        slotManager.slotsCount() == 0 -> AllIcons.Toolbar.AddSlot
        else -> AllIcons.Toolbar.Expand
      }
    }
  }

  override fun createCustomComponent(presentation: Presentation, place: String, dataContext: DataContext): JComponent {
    return object : ActionButton(this, presentation, place, ActionToolbar.DEFAULT_MINIMUM_BUTTON_SIZE) {
      private var dialogWrapper: DialogWrapper? = null

      override fun actionPerformed(event: AnActionEvent) {
        super.actionPerformed(event)
        event.project?.let { project ->


          val manager = RunToolbarSlotManager.getInstance(project)
          event.dataContext.getData(RunToolbarMainWidgetComponent.RUN_TOOLBAR_MAIN_WIDGET_COMPONENT_KEY)?.let {
            when {
              manager.slotsCount() == 0 -> {
                manager.addNewSlot()
                open(project, it)
              }
              it.isOpened -> {
                dialogWrapper?.close(DialogWrapper.OK_EXIT_CODE)
              }
              else -> {
                open(project, it)
              }
            }
          }
        }
      }

      fun open(project: Project, component: RunToolbarMainWidgetComponent) {
        dialogWrapper?.close(DialogWrapper.OK_EXIT_CODE)

        val newPane = RunToolbarExtraSlotPane(project)

        val d = DialogBuilder(project)
        d.removeAllActions()

        d.setCenterPanel(newPane.getView())
        d.showNotModal()

        component.isOpened = true
        Disposer.register(d) { component.isOpened = false }

        dialogWrapper = d.dialogWrapper
      }
    }
  }
}