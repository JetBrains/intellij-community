// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.runToolbar

import com.intellij.icons.AllIcons
import com.intellij.ide.DataManager
import com.intellij.idea.ActionsBundle
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.actionSystem.ex.CustomComponentAction
import com.intellij.openapi.actionSystem.impl.ActionButton
import com.intellij.openapi.actionSystem.impl.segmentedActionBar.SegmentedActionToolbarComponent
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import net.miginfocom.swing.MigLayout
import java.awt.Dimension
import java.awt.Point
import javax.swing.JComponent
import javax.swing.JPanel


internal class RunToolbarShowHidePopupAction : AnAction(ActionsBundle.message("action.RunToolbarShowHidePopupAction.show.popup.text")),
                                               CustomComponentAction,
                                               DumbAware,
                                               RTBarAction {

  override fun actionPerformed(e: AnActionEvent) {}

  override fun checkMainSlotVisibility(state: RunToolbarMainSlotState): Boolean {
    return state == RunToolbarMainSlotState.CONFIGURATION
  }

  override fun getActionUpdateThread() = ActionUpdateThread.BGT

  override fun update(e: AnActionEvent) {
    e.arrowIcon()?.let {
      e.presentation.icon = it
    }

    if (!RunToolbarProcess.isExperimentalUpdatingEnabled) {
      e.mainState()?.let {
        e.presentation.isEnabledAndVisible = e.presentation.isEnabledAndVisible && checkMainSlotVisibility(it)
      }
    }
    e.presentation.isEnabled = e.presentation.isEnabled && e.isFromActionToolbar
  }

  override fun createCustomComponent(presentation: Presentation, place: String): JComponent {
    val extraSlotsActionButton = ExtraSlotsActionButton(this@RunToolbarShowHidePopupAction, presentation, place)
    return object : JPanel(MigLayout("ins 0, gap 0, fill")), PopupControllerComponent {
      override fun addListener(listener: PopupControllerComponentListener) {
        extraSlotsActionButton.addListener(listener)
      }

      override fun removeListener(listener: PopupControllerComponentListener) {
        extraSlotsActionButton.removeListener(listener)
      }

      override fun updateIconImmediately(isOpened: Boolean) {
        extraSlotsActionButton.updateIconImmediately(isOpened)
      }
    }.apply {
      isOpaque = false
      add(RunWidgetResizePane(), "pos 0 0")
      add(extraSlotsActionButton, "grow")
    }
  }

  private class ExtraSlotsActionButton(action: AnAction,
                                       presentation: Presentation,
                                       place: String) : ActionButton(action, presentation, place,
                                                                     ActionToolbar.DEFAULT_MINIMUM_BUTTON_SIZE), PopupControllerComponent {
    private var project: Project? = null

    init {
      setLook(SegmentedActionToolbarComponent.segmentedButtonLook)
    }

    private fun getProject(): Project? {
      return project ?: run {
        project = CommonDataKeys.PROJECT.getData(DataManager.getInstance().getDataContext(this))
        project
      }
    }

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
      getProject()?.let {
        d.width = RunWidgetWidthHelper.getInstance(it).arrow
      }
      return d
    }
  }
}