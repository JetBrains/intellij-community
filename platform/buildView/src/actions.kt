// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.buildView

import com.intellij.build.BackendMultipleBuildsView
import com.intellij.build.BuildId
import com.intellij.build.BuildTreeConsoleView
import com.intellij.frontend.FrontendApplicationInfo
import com.intellij.frontend.FrontendType
import com.intellij.ide.CustomDataContextSerializer
import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DataKey
import com.intellij.openapi.actionSystem.remoting.ActionRemoteBehavior
import com.intellij.openapi.actionSystem.remoting.ActionRemoteBehaviorSpecification
import kotlinx.serialization.KSerializer
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
object BuildDataKeys {
  val BUILD_ID: DataKey<BuildId> = DataKey.create("BuildId")
}

internal abstract class CwmTailoredActionGroup : ActionGroup(), ActionRemoteBehaviorSpecification {
  override fun getBehavior(): ActionRemoteBehavior {
    val frontendType = FrontendApplicationInfo.getFrontendType()
    return if (frontendType is FrontendType.Remote && frontendType.isGuest())
      ActionRemoteBehavior.FrontendOnly
    else
      ActionRemoteBehavior.BackendOnly
  }
}

internal class BuildTreeContextMenuActionGroup : CwmTailoredActionGroup() {
  override fun getChildren(e: AnActionEvent?): Array<out AnAction> {
    val view = e?.getData(BuildTreeConsoleView.COMPONENT_KEY) ?: return EMPTY_ARRAY
    return view.getContextMenuGroup(e.dataContext).getChildren(e)
  }
}

internal class BuildViewToolbarActionGroup : CwmTailoredActionGroup() {
  override fun getChildren(e: AnActionEvent?): Array<out AnAction> {
    val buildId : BuildId = e?.getData(BuildDataKeys.BUILD_ID) ?: return EMPTY_ARRAY
    return BackendMultipleBuildsView.getBuildViewActions(buildId)
  }
}

internal class BuildIdDataContextSerializer : CustomDataContextSerializer<BuildId> {
  override val key: DataKey<BuildId> = BuildDataKeys.BUILD_ID
  override val serializer: KSerializer<BuildId> = BuildId.serializer()
}