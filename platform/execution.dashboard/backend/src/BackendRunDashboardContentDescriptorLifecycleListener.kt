// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.execution.dashboard.backend

import com.intellij.execution.Executor
import com.intellij.execution.impl.RUN_CONTENT_DESCRIPTOR_LIFECYCLE_TOPIC
import com.intellij.execution.impl.RunContentDescriptorLifecycleListener
import com.intellij.execution.ui.RunContentDescriptor
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity
import com.intellij.platform.execution.dashboard.BackendLuxedRunDashboardContentManager
import com.intellij.platform.execution.serviceView.isShowLuxedRunToolwindowInServicesView
import com.intellij.platform.util.coroutines.childScope

internal class BackendDashboardListenerInstaller : ProjectActivity {
  override suspend fun execute(project: Project) {
    if (!isShowLuxedRunToolwindowInServicesView()) return

    val scope = BackendLuxedRunDashboardContentManager.getInstance(project).scope.childScope("Dashboard Content Descriptor topic listening")
    project.messageBus.connect(scope)
      .subscribe(RUN_CONTENT_DESCRIPTOR_LIFECYCLE_TOPIC, BackendRunDashboardContentDescriptorLifecycleListener(project))
  }
}

private class BackendRunDashboardContentDescriptorLifecycleListener(private val project: Project) : RunContentDescriptorLifecycleListener {
  override fun beforeContentShown(descriptor: RunContentDescriptor, executor: Executor) {
    if (!isShowLuxedRunToolwindowInServicesView()) return

    BackendLuxedRunDashboardContentManager.getInstance(project).unregisterLuxedToolWindowContent(descriptor, executor)
  }

  override fun afterContentShown(descriptor: RunContentDescriptor, executor: Executor) {
    if (!isShowLuxedRunToolwindowInServicesView()) return

    BackendLuxedRunDashboardContentManager.getInstance(project).registerToolWindowContentForLuxingIfNecessary(descriptor, executor)
  }
}