// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("DEPRECATION") //  PluginManagerFilters is a backward-compatible underlying implementation

package com.intellij.ide.plugins

import com.intellij.ide.plugins.org.PluginManagerFilters

/**
 * Default implementation of [PluginManagementPolicy] that delegates logic to [com.intellij.ide.plugins.org.PluginManagerFilters]
 */
class DefaultPluginManagementPolicy : PluginManagementPolicy by DefaultPluginManagementPolicyImpl

internal object DefaultPluginManagementPolicyImpl : PluginManagementPolicy {
  override fun isUpgradeAllowed(localDescriptor: IdeaPluginDescriptor?, remoteDescriptor: IdeaPluginDescriptor?): Boolean {
    return true
  }

  override fun isDowngradeAllowed(localDescriptor: IdeaPluginDescriptor?, remoteDescriptor: IdeaPluginDescriptor?): Boolean {
    return false
  }

  override fun canEnablePlugin(descriptor: IdeaPluginDescriptor?): Boolean {
    return descriptor?.let { PluginManagerFilters.getInstance().allowInstallingPlugin(it) } ?: true
  }

  override fun canInstallPlugin(descriptor: IdeaPluginDescriptor?): Boolean {
    return canEnablePlugin(descriptor)
  }

  override fun isInstallFromDiskAllowed(): Boolean {
    return PluginManagerFilters.getInstance().allowInstallFromDisk()
  }

  override fun isPluginAutoUpdateAllowed(): Boolean {
    return true
  }
}