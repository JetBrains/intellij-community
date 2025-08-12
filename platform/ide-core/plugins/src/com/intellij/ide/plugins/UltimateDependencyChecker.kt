// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.plugins

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.service
import com.intellij.openapi.extensions.PluginId

interface UltimateDependencyChecker {
  companion object {
    @JvmStatic
    fun getInstance(): UltimateDependencyChecker = ApplicationManager.getApplication().service()
  }

  /**
   * Checks whether a plugin with the specified [pluginId] can be enabled in the current IDE context.
   *
   * The result depends on whether the Ultimate plugin is enabled and whether the given plugin requires the Ultimate plugin.
   * If the Ultimate plugin is disabled and the plugin has a dependency on it, this method will return `false`.
   *
   * @param pluginId the unique identifier of the plugin to check.
   * @return `true` if the plugin can be enabled in the current IDE context, `false` otherwise.
   */
  fun canBeEnabled(pluginId: PluginId): Boolean
}