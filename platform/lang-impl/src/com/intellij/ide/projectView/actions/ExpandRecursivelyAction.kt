// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.projectView.actions

import com.intellij.ide.HelpTooltip
import com.intellij.ide.util.treeView.AbstractTreeNode
import com.intellij.idea.ActionsBundle
import com.intellij.openapi.actionSystem.ActionManager
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
import com.intellij.openapi.util.text.StringUtil
import com.intellij.util.ui.tree.TreeUtil
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.ApiStatus.Experimental
import javax.swing.JComponent
import javax.swing.JTree
import javax.swing.tree.TreePath

@ApiStatus.Internal
@Experimental
class ExpandRecursivelyAction : DumbAwareAction(), CustomComponentAction, ActionRemoteBehaviorSpecification.Frontend {
  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.EDT

  override fun actionPerformed(e: AnActionEvent) {
    val c = e.dataContext.getData(CONTEXT_COMPONENT) as? JTree? ?: return
    val selection = c.selectionPaths ?: return
    TreeUtil.promiseExpand(c, Int.MAX_VALUE) { path ->
      selection.any { selectedPath ->
        // N.B.: isDescendant is very poorly named: a.isDescendant(b) means "b is a descendant of a".
        when {
          // Selected paths are expanded unconditionally.
          path == selectedPath -> true
          // Descendants of the selected paths are expanded unless they explicitly don't want that.
          selectedPath.isDescendant(path) -> path.isIncludedInExpandAll
          // This unusual isDescendant condition is needed because TreeUtil won't even visit
          // children if the parent doesn't match, so it'll just stop at the root node.
          // So even though the parents of the selected paths are obviously already expanded,
          // we still need to include them.
          path.isDescendant(selectedPath) -> true
          // Some irrelevant path from other parts of the tree, do not expand.
          else -> false
        }
      }
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
        val expandAllShortcut = ActionManager.getInstance().getKeyboardShortcut("ProjectViewExpandAll")
        if (expandAllShortcut != null) {
          tooltip.setDescription(StringUtil.escapeXmlEntities(ActionsBundle.message(
            "action.ExpandRecursively.shortcutHint",
            KeymapUtil.getShortcutText(expandAllShortcut)
          )))
        }
        tooltip.installOn(this)
      }
    }
}

private val TreePath.isIncludedInExpandAll: Boolean
  get() {
    // Include by default, unless the node can and does tell us otherwise.
    val node = TreeUtil.getLastUserObject(this) as? AbstractTreeNode<*> ?: return true
    return node.isIncludedInExpandAll
  }
