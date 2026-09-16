// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.lsp.api

import com.intellij.openapi.components.serviceOrNull
import com.intellij.openapi.project.Project

/**
 * Provides effective project settings for LSP server integrations.
 *
 * The settings combine provider defaults with project-specific changes.
 */
interface LspIntegrationSettings {

  /**
   * Returns the effective configuration for the server identified by [serverId].
   *
   * The result includes the provider defaults and any project-specific changes.
   * The ID must match the stable ID of a registered [LspIntegrationSettingsProvider].
   */
  fun getPluginConfiguration(serverId: String): LspPluginServerConfiguration

  companion object {
    /** Returns the project settings, or `null` when no settings service is available. */
    @JvmStatic
    fun getInstanceOrNull(project: Project): LspIntegrationSettings? = project.serviceOrNull<LspIntegrationSettings>()
  }
}
