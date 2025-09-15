// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.execution.dashboard.splitApi.frontend

import com.intellij.execution.dashboard.RunDashboardManager
import com.intellij.execution.dashboard.RunDashboardManagerProxy
import com.intellij.openapi.project.Project
import com.intellij.platform.execution.serviceView.shouldEnableServicesViewInCurrentEnvironment
import com.intellij.util.PlatformUtils

internal class FrontendRunDashboardManagerProxy: RunDashboardManagerProxy {
  override fun isEnabled(): Boolean {
    return PlatformUtils.isJetBrainsClient() && shouldEnableServicesViewInCurrentEnvironment()
  }

  override fun getManager(project: Project): RunDashboardManager {
    return FrontendRunDashboardManager.getInstance(project)
  }
}