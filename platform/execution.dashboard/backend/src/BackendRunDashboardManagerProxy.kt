// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.execution.dashboard.backend

import com.intellij.execution.dashboard.RunDashboardManager
import com.intellij.execution.dashboard.RunDashboardManagerProxy
import com.intellij.openapi.project.Project
import com.intellij.platform.execution.dashboard.RunDashboardManagerImpl
import com.intellij.util.PlatformUtils

internal class BackendRunDashboardManagerProxy : RunDashboardManagerProxy {
  override fun isEnabled(): Boolean {
    return !PlatformUtils.isJetBrainsClient()
  }

  override fun getManager(project: Project): RunDashboardManager {
    return RunDashboardManagerImpl.getInstance(project)
  }
}