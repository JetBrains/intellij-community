// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.actionSystem

import com.intellij.icons.AllIcons
import org.jetbrains.annotations.ApiStatus

/**
 * Actions which can behave as a group if they > 1 or as one action if action is only one
 */
@ApiStatus.Internal
class MergeableActions(
  private val originalGroup: ActionGroup,
): ActionGroup() {

  override fun getActionUpdateThread(): ActionUpdateThread {
    return ActionUpdateThread.BGT
  }

  override fun update(e: AnActionEvent) {
    val children = originalGroup.getChildren(e)
    var visibleChildrenCount = 0
    for (child in children) {
      child.update(e)
      if (e.presentation.isVisible) {
        visibleChildrenCount++
        if (visibleChildrenCount > 1) break
      }
    }
    e.presentation.isPopupGroup = visibleChildrenCount > 1
    e.presentation.isEnabledAndVisible = visibleChildrenCount > 0
    e.presentation.icon = AllIcons.Actions.More
  }

  override fun getChildren(e: AnActionEvent?): Array<AnAction> {
    return originalGroup.getChildren(e)
  }

}