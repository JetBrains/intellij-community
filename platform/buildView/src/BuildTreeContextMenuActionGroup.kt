// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.buildView

import com.intellij.build.BuildTreeConsoleView
import com.intellij.frontend.FrontendApplicationInfo
import com.intellij.frontend.FrontendType
import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.remoting.ActionRemoteBehavior
import com.intellij.openapi.actionSystem.remoting.ActionRemoteBehaviorSpecification

private class BuildTreeContextMenuActionGroup : ActionGroup(), ActionRemoteBehaviorSpecification {
  override fun getBehavior(): ActionRemoteBehavior {
    val frontendType = FrontendApplicationInfo.getFrontendType()
    return if (frontendType is FrontendType.Remote && frontendType.isGuest())
      ActionRemoteBehavior.FrontendOnly
    else
      ActionRemoteBehavior.BackendOnly
  }

  override fun getChildren(e: AnActionEvent?): Array<out AnAction> {
    val view = e?.getData(BuildTreeConsoleView.COMPONENT_KEY) ?: return emptyArray()
    return view.getContextMenuGroup(e.dataContext).getChildren(e)
  }
}