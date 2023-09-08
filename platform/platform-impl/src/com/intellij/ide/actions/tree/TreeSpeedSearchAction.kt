// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.actions.tree

import com.intellij.icons.AllIcons
import com.intellij.idea.ActionsBundle
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.toolWindow.InternalDecoratorImpl
import com.intellij.util.ui.UIUtil
import java.awt.Component
import javax.swing.JTree
import javax.swing.SwingUtilities

class TreeSpeedSearchAction : DumbAwareAction(
  ActionsBundle.messagePointer("action.Tree-speedSearch.text"),
  AllIcons.Actions.Find,
) {

  companion object {
    const val ID = "Tree-speedSearch"
  }

  override fun getActionUpdateThread() = ActionUpdateThread.EDT

  override fun update(e: AnActionEvent) {
    val contextComponent = e.dataContext.getData(PlatformDataKeys.CONTEXT_COMPONENT) ?: return
    val handler = findHandler(contextComponent)
    e.presentation.isVisible = handler != null
    e.presentation.isEnabled = handler != null && handler.isSpeedSearchAvailable() && !handler.isSpeedSearchActive()
    e.presentation.putClientProperty(
      Presentation.PROP_KEYBOARD_SHORTCUT_SUFFIX,
      ActionsBundle.message("action.Tree-speedSearch.shortcutTextSuffix")
    )
  }

  private fun findHandler(contextComponent: Component): SpeedSearchActionHandler? =
    contextComponent.getSpeedSearchActionHandler() ?: findInToolWindow(contextComponent)

  private fun findInToolWindow(contextComponent: Component): SpeedSearchActionHandler? {
    val toolWindowDecorator = UIUtil.getParentOfType(InternalDecoratorImpl::class.java, contextComponent)
    val trees = UIUtil.uiTraverser(toolWindowDecorator)
      .filter { it is JTree && it.isShowing }
      .toMutableList()
    trees.sortBy {
      val pointOnToolWindow = SwingUtilities.convertPoint(it.parent, it.location, toolWindowDecorator)
      // sort by distance from the top-left corner
      pointOnToolWindow.x * pointOnToolWindow.x + pointOnToolWindow.y * pointOnToolWindow.y
    }
    return trees.firstNotNullOfOrNull { tree ->
      val handler = tree.getSpeedSearchActionHandler()
      if (handler != null && handler.isSpeedSearchAvailable()) {
        handler
      }
      else {
        null
      }
    }
  }

  override fun actionPerformed(e: AnActionEvent) {
    val contextComponent = e.dataContext.getData(PlatformDataKeys.CONTEXT_COMPONENT) ?: return
    val handler = findHandler(contextComponent) ?: return
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
