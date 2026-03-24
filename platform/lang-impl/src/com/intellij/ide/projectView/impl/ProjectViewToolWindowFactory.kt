// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.projectView.impl

import com.intellij.icons.AllIcons
import com.intellij.ide.projectView.ProjectView
import com.intellij.openapi.components.serviceAsync
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.openapi.wm.ex.ProjectFrameCapabilitiesService
import com.intellij.openapi.wm.ex.ProjectFrameCapability
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.VisibleForTesting
import javax.swing.Icon

@ApiStatus.Internal
@VisibleForTesting
class ProjectViewToolWindowFactory : ToolWindowFactory, DumbAware {
  override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
    (ProjectView.getInstance(project) as ProjectViewImpl).setupImpl(toolWindow)
  }

  override val icon: Icon
    get() = AllIcons.Toolwindows.ToolWindowProject

  override suspend fun isApplicableAsync(project: Project): Boolean {
    return !serviceAsync<ProjectFrameCapabilitiesService>().has(project, ProjectFrameCapability.SUPPRESS_PROJECT_VIEW)
  }
}
