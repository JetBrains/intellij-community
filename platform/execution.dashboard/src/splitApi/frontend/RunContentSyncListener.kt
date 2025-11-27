// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.execution.dashboard.splitApi.frontend

import com.intellij.execution.rpc.RPC_SYNC_RUN_TOPIC
import com.intellij.execution.rpc.RunContentLiveIconSyncEvent
import com.intellij.execution.ui.RunContentManagerImpl.Companion.getLiveIndicator
import com.intellij.openapi.application.EDT
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.ScalableIcon
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.platform.execution.dashboard.RunDashboardCoroutineScopeProvider
import com.intellij.platform.rpc.topics.ProjectRemoteTopic
import com.intellij.platform.rpc.topics.ProjectRemoteTopicListener
import com.intellij.ui.ExperimentalUI
import com.intellij.ui.icons.loadIconCustomVersionOrScale
import com.intellij.util.ui.EmptyIcon
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.jetbrains.annotations.ApiStatus
import javax.swing.Icon

@ApiStatus.Internal
class RunContentSyncListener() : ProjectRemoteTopicListener<RunContentLiveIconSyncEvent> {
  private val toolWindowIdToBaseIcon: MutableMap<String, Icon> = HashMap()

  override val topic: ProjectRemoteTopic<RunContentLiveIconSyncEvent> = RPC_SYNC_RUN_TOPIC

  override fun handleEvent(project: Project, event: RunContentLiveIconSyncEvent) {
    val cs = RunDashboardCoroutineScopeProvider.getInstance(project).cs
    val toolWindow = ToolWindowManager.getInstance(project).getToolWindow(event.toolwindowId) ?: return
    val toolWindowIcon = initializeOrGetBaseToolWindowIcon(toolWindow)
    cs.launch(Dispatchers.EDT) {
      toolWindow.setIcon(if (event.alive) getLiveIndicator(toolWindowIcon) else toolWindowIcon ?: EmptyIcon.ICON_13)
    }
  }

  private fun initializeOrGetBaseToolWindowIcon(toolWindow: ToolWindow) : Icon? {
    return toolWindowIdToBaseIcon[toolWindow.id] ?: run {
      var toolWindowIcon = toolWindow.icon ?: return null

      if (ExperimentalUI.isNewUI() && toolWindowIcon is ScalableIcon) {
        toolWindowIcon = loadIconCustomVersionOrScale(icon = toolWindowIcon, size = 20)
      }

      toolWindowIdToBaseIcon[toolWindow.id] = toolWindowIcon
      toolWindowIcon
    }
  }
}