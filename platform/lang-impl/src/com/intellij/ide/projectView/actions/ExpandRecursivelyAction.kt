// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.projectView.actions

import com.intellij.ide.HelpTooltip
import com.intellij.idea.ActionsBundle
import com.intellij.openapi.actionSystem.ActionToolbar
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.PlatformCoreDataKeys.CONTEXT_COMPONENT
import com.intellij.openapi.actionSystem.Presentation
import com.intellij.openapi.actionSystem.ex.CustomComponentAction
import com.intellij.openapi.actionSystem.impl.ActionButton
import com.intellij.openapi.actionSystem.remoting.ActionRemoteBehaviorSpecification
import com.intellij.openapi.keymap.KeymapUtil
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.util.registry.Registry
import com.intellij.util.ui.tree.TreeUtil
import org.jetbrains.annotations.ApiStatus.Experimental
import javax.swing.JComponent
import javax.swing.JTree

@Experimental
class ExpandRecursivelyAction : DumbAwareAction(), CustomComponentAction, ActionRemoteBehaviorSpecification.Frontend {
  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.EDT

  override fun actionPerformed(e: AnActionEvent) {
    val c = e.dataContext.getData(CONTEXT_COMPONENT) as? JTree? ?: return
    val selection = c.selectionPaths ?: return
    TreeUtil.promiseExpand(c, Int.MAX_VALUE) { path ->
      // This unusual isDescendant condition is needed because TreeUtil won't even visit
      // children if the parent doesn't match, so it'll just stop at the root node.
      // So even though the parents of the selected paths are obviously already expanded,
      // we still need to include them.
      selection.any { it.isDescendant(path) || path.isDescendant(it) }
    }
  }

  override fun update(e: AnActionEvent) {
    val c = e.dataContext.getData(CONTEXT_COMPONENT)
    e.presentation.isEnabled = c is JTree && c.selectionCount > 0
    e.presentation.isVisible = Registry.`is`("ide.project.view.replace.expand.all.with.expand.recursively", true)
  }

  override fun createCustomComponent(presentation: Presentation, place: String): JComponent =
    object : ActionButton(this, presentation, place, ActionToolbar.DEFAULT_MINIMUM_BUTTON_SIZE) {
      override fun updateToolTipText() {
        super.updateToolTipText()
        HelpTooltip.dispose(this)
        val tooltip = HelpTooltip()
        tooltip.setTitle(myPresentation.text).setShortcut(shortcutText)
        val expandAllShortcut = KeymapUtil.getShortcutText("ProjectViewExpandAll")
        if (expandAllShortcut.isNotEmpty()) {
          tooltip.setDescription(ActionsBundle.message("action.ExpandRecursively.shortcutHint", expandAllShortcut)).installOn(this)
        }
      }
    }
}
