// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.dashboard

import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.project.Project
import org.jetbrains.annotations.ApiStatus

/**
 * Provides different implementations of [RunDashboardManager] depending on the IDE mode (client, backend, monolith).
 * It is required to have this abstraction layer and access [RunDashboardManager] methods with it and not directly to be able
 * to fall back to the default services view implementation (that works through LUX in split mode).
 *
 * After it is stabilised, the proxy has to be deleted. Currently, the class is intentionally placed to the execution api module
 * to avoid introducing circular dependencies between the dashboard.frontend/backend/shared modules and execution.impl
 */
@ApiStatus.Internal
interface RunDashboardManagerProxy {
  companion object {
    protected val EP_NAME: ExtensionPointName<RunDashboardManagerProxy> =
      ExtensionPointName.create<RunDashboardManagerProxy>("com.intellij.runDashboardManagerProxy")

    @JvmStatic
    fun getInstance(project: Project): RunDashboardManager {
      return EP_NAME.findFirstSafe { it.isEnabled() }?.getManager(project)
             ?: error("No RunDashboardManager implementation found")
    }
  }

  fun isEnabled(): Boolean

  fun getManager(project: Project): RunDashboardManager
}