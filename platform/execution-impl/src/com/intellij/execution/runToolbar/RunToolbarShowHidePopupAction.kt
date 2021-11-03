// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.runToolbar

import com.intellij.icons.AllIcons
import com.intellij.idea.ActionsBundle
import com.intellij.openapi.actionSystem.ActionToolbar
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.Presentation
import com.intellij.openapi.actionSystem.ex.CustomComponentAction
import com.intellij.openapi.actionSystem.impl.ActionButton
import com.intellij.openapi.project.DumbAware
import java.awt.Dimension
import java.awt.Point
import javax.swing.JComponent


class RunToolbarShowHidePopupAction : AnAction(ActionsBundle.message("action.RunToolbarShowHidePopupAction.show.popup.text")), CustomComponentAction, DumbAware, RTBarAction {

  override fun actionPerformed(e: AnActionEvent) {

  }

  override fun checkMainSlotVisibility(state: RunToolbarMainSlotState): Boolean {
    return state == RunToolbarMainSlotState.CONFIGURATION
  }

  override fun update(e: AnActionEvent) {
    e.arrowIcon()?.let {
      e.presentation.icon = it
    }

    if (!RunToolbarProcess.isExperimentalUpdatingEnabled) {
      e.mainState()?.let {
        e.presentation.isEnabledAndVisible = e.presentation.isEnabledAndVisible && checkMainSlotVisibility(it)
      }
    }
  }

  override fun createCustomComponent(presentation: Presentation, place: String): JComponent {
    return ExtraSlotsActionButton(this, presentation, place)
  }

  private class ExtraSlotsActionButton(action: AnAction,
                                       presentation: Presentation,
                                       place: String) : ActionButton(action, presentation, place,
                                                                     ActionToolbar.DEFAULT_MINIMUM_BUTTON_SIZE), PopupControllerComponent {

    override fun addNotify() {
      super.addNotify()

      mousePosition?.let {
        val bounds = this.bounds
        bounds.location = Point(0, 0)

        if (bounds.contains(it)) {
          myRollover = true
          repaint()
        }
      }
    }

    override fun actionPerformed(event: AnActionEvent) {
      val list = mutableListOf<PopupControllerComponentListener>()
      list.addAll(listeners)
      list.forEach { it.actionPerformedHandler() }
    }

    private val listeners = mutableListOf<PopupControllerComponentListener>()

    override fun addListener(listener: PopupControllerComponentListener) {
      listeners.add(listener)
    }

    override fun removeListener(listener: PopupControllerComponentListener) {
      listeners.remove(listener)
    }

    override fun updateIconImmediately(isOpened: Boolean) {
      myIcon = if (isOpened) AllIcons.Toolbar.Collapse
      else AllIcons.Toolbar.Expand
    }

    override fun getPreferredSize(): Dimension {
      val d = super.getPreferredSize()
      d.width = FixWidthSegmentedActionToolbarComponent.ARROW_WIDTH
      return d
    }
  }
}