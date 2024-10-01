// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Internal
package com.intellij.ide.actions

import com.intellij.icons.AllIcons
import com.intellij.idea.ActionsBundle
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.util.IconLoader.createLazy
import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.wm.ex.ToolWindowManagerEx
import com.intellij.toolWindow.ToolWindowDefaultLayoutManager
import com.intellij.util.ui.EmptyIcon
import org.jetbrains.annotations.ApiStatus.Internal

@Internal
class RestoreNamedLayoutActionGroup : ActionGroup(), DumbAware {

  private val childrenCache = NamedLayoutListBasedCache<AnAction>(
    listOf(RestoreNamedLayoutAction(ToolWindowDefaultLayoutManager.FACTORY_DEFAULT_LAYOUT_NAME)), 1
  ) {
    RestoreNamedLayoutAction(it)
  }

  override fun getChildren(e: AnActionEvent?): Array<AnAction> = childrenCache.getCachedOrUpdatedArray(AnAction.EMPTY_ARRAY)

  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

  private class RestoreNamedLayoutAction(@NlsSafe private val layoutName: String) : DumbAwareAction() {

    override fun getActionUpdateThread() = ActionUpdateThread.BGT

    override fun actionPerformed(e: AnActionEvent) {
      val project = e.project ?: return
      val layoutManager = ToolWindowDefaultLayoutManager.getInstance()
      layoutManager.activeLayoutName = layoutName
      ToolWindowManagerEx.getInstanceEx(project).setLayout(layoutManager.getLayoutCopy())
    }

    override fun update(e: AnActionEvent) {
      super.update(e)
      e.presentation.isEnabled = e.project != null
      if (layoutName == ToolWindowDefaultLayoutManager.FACTORY_DEFAULT_LAYOUT_NAME) {
        val restoreFactoryDefaultLayoutAction = ActionManager.getInstance().getAction(RestoreFactoryDefaultLayoutAction.ID)
        e.presentation.text = restoreFactoryDefaultLayoutAction.templatePresentation.textWithMnemonic
        e.presentation.description = restoreFactoryDefaultLayoutAction.templatePresentation.description
      }
      else {
        e.presentation.setText({ layoutName }, false)
        e.presentation.description = ActionsBundle.message("action.RestoreNamedLayout.description", layoutName)
      }
      e.presentation.icon = if (ToolWindowDefaultLayoutManager.getInstance().activeLayoutName == layoutName) {
        currentIcon
      }
      else {
        emptyIcon
      }
    }

  }

}

private val currentIcon = createLazy {
  AllIcons.Actions.Forward
}

private val emptyIcon = createLazy {
  EmptyIcon.create(AllIcons.Actions.Forward.iconWidth, AllIcons.Actions.Forward.iconHeight)
}
