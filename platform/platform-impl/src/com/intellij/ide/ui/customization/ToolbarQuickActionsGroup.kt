// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.ui.customization

import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.remoting.ActionRemoteBehaviorSpecification
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
class ToolbarQuickActionsGroup: ActionGroup(), ActionRemoteBehaviorSpecification.Frontend {

  override fun getChildren(e: AnActionEvent?): Array<AnAction> {
    val groupID = ActionManager.getInstance().getId(this);
    return QUICK_ACTION_EP_NAME.extensionList.filter { it.listGroupID == groupID }.map { infoToAction(it) }.toTypedArray()
  }

  private fun infoToAction(bean: ToolbarAddQuickActionInfoBean): AnAction = ToolbarAddQuickActionsAction(bean.instance)
}