// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.actions.speedSearch

import com.intellij.idea.ActionsBundle
import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.PlatformDataKeys
import com.intellij.openapi.actionSystem.ex.ActionUtil
import com.intellij.openapi.actionSystem.remoting.ActionRemoteBehaviorSpecification
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.toolWindow.InternalDecoratorImpl
import com.intellij.util.ui.UIUtil
import java.awt.Component
import javax.swing.SwingUtilities

class SpeedSearchAction : DumbAwareAction(), ActionRemoteBehaviorSpecification.Frontend {

  init {
    isEnabledInModalContext = true
  }

  companion object {
    const val ID = "SpeedSearch"
  }

  override fun getActionUpdateThread() = ActionUpdateThread.EDT

  override fun update(e: AnActionEvent) {
    val handler = findHandler(e)
    e.presentation.isVisible = handler != null
    e.presentation.isEnabled = handler != null && handler.isSpeedSearchAvailable() && !handler.isSpeedSearchActive()
    e.presentation.putClientProperty(
      ActionUtil.KEYBOARD_SHORTCUT_SUFFIX,
      ActionsBundle.message("action.SpeedSearch.shortcutTextSuffix")
    )
  }

  private fun findHandler(e: AnActionEvent): SpeedSearchActionHandler? {
    val contextComponent = e.dataContext.getData(PlatformDataKeys.CONTEXT_COMPONENT) ?: return null
    return if (e.place == ActionPlaces.KEYBOARD_SHORTCUT) {
      contextComponent.getSpeedSearchActionHandler()
    }
    else {
      findInToolWindow(contextComponent)
    }
  }

  private fun findInToolWindow(contextComponent: Component): SpeedSearchActionHandler? {
    val toolWindowDecorator = UIUtil.getParentOfType(InternalDecoratorImpl::class.java, contextComponent)
    val handlers = UIUtil.uiTraverser(toolWindowDecorator)
      .mapNotNull { it.getSpeedSearchActionHandler() }
      .filter { it.targetComponent.isShowing }
      .toMutableList()
    handlers.sortBy {
      val pointOnToolWindow = SwingUtilities.convertPoint(it.targetComponent.parent, it.targetComponent.location, toolWindowDecorator)
      // sort by distance from the top-left corner
      pointOnToolWindow.x * pointOnToolWindow.x + pointOnToolWindow.y * pointOnToolWindow.y
    }
    return handlers.firstOrNull { it.isSpeedSearchAvailable() }
  }

  override fun actionPerformed(e: AnActionEvent) {
    val handler = findHandler(e) ?: return
    if (e.place == ActionPlaces.KEYBOARD_SHORTCUT) {
      handler.requestFocus = false
      handler.showGotItTooltip = true
    }
    else { // invoked from the tool window menu
      handler.requestFocus = true
      handler.showGotItTooltip = false
    }
    handler.activateSpeedSearch()
  }

}
