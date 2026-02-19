// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.plugins.newui

import com.intellij.ide.plugins.IdeaPluginDescriptor
import com.intellij.ide.plugins.PluginEnabledState
import com.intellij.ide.plugins.PluginEnabler
import com.intellij.openapi.extensions.PluginId
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
class SessionStatePluginEnabler(private val session: PluginManagerSession) : PluginEnabler {
  val pluginsToEnable: MutableSet<PluginId> = mutableSetOf()
  val pluginsToDisable: MutableSet<PluginId> = mutableSetOf()

  override fun isDisabled(pluginId: PluginId): Boolean {
    return session.pluginStates[pluginId]?.isDisabled ?: false
  }

  override fun enable(descriptors: Collection<IdeaPluginDescriptor>): Boolean {
    val pluginIds = descriptors.map { it.pluginId }
    pluginsToEnable.addAll(pluginIds)
    pluginIds.forEach { session.pluginStates[it] = PluginEnabledState.ENABLED }
    return true
  }

  override fun disable(descriptors: Collection<IdeaPluginDescriptor>): Boolean {
    val pluginIds = descriptors.map { it.pluginId }
    pluginsToDisable.addAll(descriptors.map { it.pluginId })
    pluginIds.forEach { session.pluginStates[it] = PluginEnabledState.DISABLED }
    return true
  }
}