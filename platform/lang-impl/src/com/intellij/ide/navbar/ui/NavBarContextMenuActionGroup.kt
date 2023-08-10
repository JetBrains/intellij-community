// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.navbar.ui

import com.intellij.ide.navigationToolbar.NavBarModelExtension
import com.intellij.ide.ui.customization.CustomActionsSchema
import com.intellij.openapi.actionSystem.*

internal class NavBarContextMenuActionGroup : ActionGroup() {

  override fun getChildren(e: AnActionEvent?): Array<AnAction> {
    if (e == null) {
      return EMPTY_ARRAY
    }
    val dataContext = e.dataContext
    val popupGroupId = contextMenuActionGroupId(dataContext::getData)
    val group = CustomActionsSchema.getInstance().getCorrectedAction(popupGroupId) as ActionGroup?
                ?: return EMPTY_ARRAY
    return group.getChildren(e)
  }
}

private fun contextMenuActionGroupId(dataProvider: DataProvider): String {
  for (modelExtension in NavBarModelExtension.EP_NAME.extensionList) {
    return modelExtension.getPopupMenuGroup(dataProvider)
           ?: continue
  }
  return IdeActions.GROUP_NAVBAR_POPUP
}
