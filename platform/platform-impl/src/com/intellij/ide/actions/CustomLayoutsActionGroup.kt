// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.actions

import com.intellij.idea.ActionsBundle
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.actionSystem.remoting.ActionRemoteBehaviorSpecification
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.wm.ex.ToolWindowManagerEx
import com.intellij.toolWindow.ToolWindowDefaultLayoutManager
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
class CustomLayoutsActionGroup : ActionGroup(), DumbAware, ActionRemoteBehaviorSpecification.Frontend {

  private val childrenCache = NamedLayoutListBasedCache<AnAction>(emptyList(), 0) {
    CustomLayoutActionGroup(it)
  }

  override fun getChildren(e: AnActionEvent?): Array<AnAction> =
    if (e == null) {
      AnAction.EMPTY_ARRAY
    }
    else {
      childrenCache.getCachedOrUpdatedArray(AnAction.EMPTY_ARRAY)
    }

  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

  override fun update(e: AnActionEvent) {
    super.update(e)
    e.presentation.isVisible = e.place != ActionPlaces.ACTION_SEARCH // to avoid confusion with the upper level LayoutsGroup
    e.presentation.isPopupGroup = e.place != ActionPlaces.MAIN_MENU // to be used as a popup, e.g., in toolbars
  }

  private class CustomLayoutActionGroup(
    @NlsSafe private val layoutName: String
  ) : ActionGroup(ActionsBundle.message("group.CustomLayoutActionsGroup.text"), true), DumbAware, Toggleable {

    private val commonChildren = listOf<AnAction>(
      RenameLayoutAction(layoutName),
      Separator(),
      Delete(layoutName),
    )

    private val currentLayoutChildren = (listOf<AnAction>(
      Restore(),
      Save(layoutName),
    ) + commonChildren).toTypedArray()

    private val nonCurrentLayoutChildren = (listOf<AnAction>(
      Apply(layoutName),
    ) + commonChildren).toTypedArray()

    override fun getActionUpdateThread() = ActionUpdateThread.BGT

    override fun update(e: AnActionEvent) {
      e.presentation.setText(layoutName, false)
      e.presentation.isVisible = layoutName.isNotBlank() // Just in case the layout name is corrupted somehow.
      Toggleable.setSelected(e.presentation, manager.activeLayoutName == layoutName)
    }

    override fun getChildren(e: AnActionEvent?): Array<AnAction> =
      if (manager.activeLayoutName == layoutName) {
        currentLayoutChildren
      }
      else {
        nonCurrentLayoutChildren
      }

    private class Apply(private val layoutName: String) : DumbAwareAction() {
      init {
        templatePresentation.text = ActionsBundle.message("action.CustomLayoutActionsGroup.Apply.text")
      }

      override fun getActionUpdateThread() = ActionUpdateThread.BGT

      override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val layoutManager = ToolWindowDefaultLayoutManager.getInstance()
        layoutManager.activeLayoutName = layoutName
        ToolWindowManagerEx.getInstanceEx(project).setLayout(layoutManager.getLayoutCopy())
      }
    }

    private class Restore : AnActionWrapper(ActionManager.getInstance().getAction("RestoreDefaultLayout")) {
      override fun update(e: AnActionEvent) {
        super.update(e)
        e.presentation.isVisible = true // overrides RestoreDefaultLayoutAction
        e.presentation.text = ActionsBundle.message("action.CustomLayoutActionsGroup.Restore.text")
      }
    }

    private class Save(layoutName: String) : StoreNamedLayoutAction(layoutName) {
      init {
        templatePresentation.text = ActionsBundle.message("action.CustomLayoutActionsGroup.Save.text")
      }
    }

    private class Delete(layoutName: String) : DeleteNamedLayoutAction(layoutName) {
      init {
        templatePresentation.text = ActionsBundle.message("action.CustomLayoutActionsGroup.Delete.text")
      }
    }

  }

}

private val manager get() = ToolWindowDefaultLayoutManager.getInstance()
