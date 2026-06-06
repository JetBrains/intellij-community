// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ijent.community.ui.actions.dashboard

import com.intellij.openapi.project.Project
import com.intellij.platform.ijent.IjentApi
import com.intellij.platform.ijent.IjentSession
import com.intellij.platform.ijent.community.ui.actions.IjentImplBundle
import kotlinx.coroutines.flow.flowOf
import javax.swing.JComponent

internal class IjentDashboardTabEnvVars : IjentDashboardTab {
  override val name: String
    get() = IjentImplBundle.message("tab.title.ijent.dashboard.environment.variables")

  override fun createComponent(projects: List<Project>, ijentApi: IjentApi, ijentSession: IjentSession, parentComponent: JComponent?): JComponent {
    return EnvironmentVariablesDashboard(flowOf(
      EnvironmentVariablesDashboard.FetchEnvVarsMode {
        ijentApi.exec.fetchLoginShellEnvVariables()
      }
    )).component
  }
}