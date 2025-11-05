// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.execution.dashboard.splitApi.frontend

import com.intellij.execution.findContentValue
import com.intellij.execution.rpc.OpenToolWindowEvent
import com.intellij.openapi.application.EDT
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.platform.execution.dashboard.RunDashboardCoroutineScopeProvider
import com.intellij.platform.rpc.topics.ProjectRemoteTopic
import com.intellij.platform.rpc.topics.ProjectRemoteTopicListener
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class FrontendOpenToolwindowEvent : ProjectRemoteTopicListener<OpenToolWindowEvent>  {
  override val topic: ProjectRemoteTopic<OpenToolWindowEvent>
    get() = ProjectRemoteTopic("OpenToolWindowEvent", OpenToolWindowEvent.serializer())

  override fun handleEvent(project: Project, event: OpenToolWindowEvent) {
    //val runnable = event.contentId?.findContentValue()?.activationCallback
    val runnable = null
    logger<FrontendOpenToolwindowEvent>().info("Opening toolwindow ${event.toolwindowId}")
    RunDashboardCoroutineScopeProvider.getInstance(project).cs.launch(Dispatchers.EDT) {
      val toolwindow = ToolWindowManager.getInstance(project).getToolWindow(event.toolwindowId) ?: return@launch
      toolwindow.activate (runnable, event.focus, event.focus)
    }
  }

}