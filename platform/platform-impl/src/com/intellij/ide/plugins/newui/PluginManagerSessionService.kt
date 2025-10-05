// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.plugins.newui

import com.intellij.concurrency.ConcurrentCollectionFactory
import com.intellij.ide.plugins.*
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.extensions.PluginId
import com.intellij.openapi.util.Pair
import org.jetbrains.annotations.ApiStatus
import java.util.*
import java.util.concurrent.ConcurrentHashMap

/**
 * A session is required to store plugin information between calls.
 * Both the client and the host should have their own session.
 * The session is created when PluginManagerConfigurable opens and ends when the user presses cancel or apply.
 */
@Service
@ApiStatus.Internal
class PluginManagerSessionService {
  private val sessions: MutableMap<UUID, PluginManagerSession> = ConcurrentHashMap()

  fun createSession(sessionId: String): PluginManagerSession {
    return sessions.computeIfAbsent(UUID.fromString(sessionId)) { PluginManagerSession() }
  }

  fun getSession(sessionId: UUID): PluginManagerSession? {
    return sessions[sessionId]
  }

  fun getSession(sessionId: String): PluginManagerSession? {
    return sessions[UUID.fromString(sessionId)]
  }

  fun removeSession(sessionId: String) {
    sessions.remove(UUID.fromString(sessionId))
  }

  fun removeSession(sessionId: UUID) {
    sessions.remove(sessionId)
  }

  companion object {
    @JvmStatic
    fun getInstance(): PluginManagerSessionService = service()
  }
}

@ApiStatus.Internal
class PluginManagerSession {
  val dynamicPluginsToInstall: MutableMap<PluginId, PendingDynamicPluginInstall> = ConcurrentHashMap()
  val pluginsToRemoveOnCancel: MutableSet<IdeaPluginDescriptorImpl> = ConcurrentCollectionFactory.createConcurrentSet()
  val dynamicPluginsToUninstall: MutableSet<IdeaPluginDescriptor> = ConcurrentCollectionFactory.createConcurrentSet()
  val dependentToRequiredListMap: MutableMap<PluginId, MutableSet<PluginId>> = ConcurrentHashMap()
  val installsInProgress:  MutableMap<PluginId, PluginUiModel> = ConcurrentHashMap()
  val updatesInProgress:  MutableMap<PluginId, PluginUiModel> = ConcurrentHashMap()
  var isUiDisposedWithApply: Boolean = false

  val errorPluginsToDisable: MutableSet<PluginId> = ConcurrentCollectionFactory.createConcurrentSet()
  val uninstalledPlugins: MutableSet<PluginId> = ConcurrentCollectionFactory.createConcurrentSet()
  val pluginStates: MutableMap<PluginId, PluginEnabledState?> = mutableMapOf()
  val statesDiff: MutableMap<IdeaPluginDescriptor, Pair<PluginEnableDisableAction, PluginEnabledState>> = ConcurrentHashMap()
  var updateService: PluginUpdatesService? = null
  var needRestart = false

  // The next 2 methods was mooved from com.intellij.ide.plugins.InstalledPluginsTableModel
  fun isPluginDisabled(pluginId: PluginId): Boolean {
    return pluginStates[pluginId]?.isDisabled ?: true
  }
  
  fun isPluginEnabled(pluginId: PluginId): Boolean {
    return pluginStates[pluginId]?.isEnabled ?: true
  }
}