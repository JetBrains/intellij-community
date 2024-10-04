// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.plugins

import com.intellij.openapi.components.service
import org.jetbrains.annotations.ApiStatus

/**
 * Common service to modify behaviour of plugin management UI/UX.
 *
 * This service doesn't affect core plugin management in [com.intellij.ide.plugins.PluginManagerCore], just the UI and update process.
 * For early plugin loading restrictions, see [com.intellij.ide.plugins.DisabledPluginsState]
 *
 * This service can be overridden from plugins using service overrides
 *
 * @see DefaultPluginManagementPolicy
 */
@ApiStatus.Internal
@ApiStatus.Experimental
interface PluginManagementPolicy {

  /**
   * Whether to show that an upgrade is available for a plugin.
   *
   * Pay close attention to the fact that if multiple plugin sources are present, there can be more than one [remoteDescriptor]
   * with a version higher than one in [localDescriptor].
   *
   * @see [com.intellij.ide.plugins.PluginNode]
   */
  fun isUpgradeAllowed(localDescriptor: IdeaPluginDescriptor?, remoteDescriptor: IdeaPluginDescriptor?): Boolean

  /**
   * Allows downloading and installing a lower version of [localDescriptor]
   *
   * Pay close attention to the fact that if multiple plugin sources are present, there can be more than one [remoteDescriptor]
   * with a version lower than one in [localDescriptor].
   *
   * @see [com.intellij.ide.plugins.PluginNode]
   */
  fun isDowngradeAllowed(localDescriptor: IdeaPluginDescriptor?, remoteDescriptor: IdeaPluginDescriptor?): Boolean

  /**
   * Plugin with [descriptor] will be grayed out in the Marketplace UI and a warning will be show that this plugin is unavailable
   * if the user tries to enable it. Handles plugins that are already installed.
   */
  fun canEnablePlugin(descriptor: IdeaPluginDescriptor?): Boolean

  /**
   * Allows or prohibits installation of [descriptor] from remote repositories
   */
  fun canInstallPlugin(descriptor: IdeaPluginDescriptor?): Boolean

  /**
   * Allows or prohibits installation of plugins from disk. Disables relevant item in the cog menu of Marketplace UI
   *
   * @see com.intellij.ide.plugins.InstallFromDiskAction
   */
  fun isInstallFromDiskAllowed(): Boolean

  companion object {
    @JvmStatic
    fun getInstance(): PluginManagementPolicy = service()
  }
}