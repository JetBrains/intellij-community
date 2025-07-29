// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.plugins

import com.intellij.ide.DataManager
import com.intellij.ide.plugins.marketplace.InitSessionResult
import com.intellij.ide.plugins.newui.*
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.asContextElement
import com.intellij.openapi.application.ex.ApplicationManagerEx
import com.intellij.openapi.components.service
import com.intellij.openapi.extensions.PluginId
import com.intellij.openapi.options.ex.Settings
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.registry.Registry
import kotlinx.coroutines.*
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.NonNls
import java.util.*
import java.util.function.Consumer

@ApiStatus.Internal
open class InstalledPluginsTableModel @JvmOverloads constructor(
  protected val project: Project?,
  initSessionResult: InitSessionResult? = null,
  @JvmField val mySessionId: UUID = UUID.randomUUID(),
) {
  var coroutineScope: CoroutineScope = service<FrontendRpcCoroutineContext>().coroutineScope

  @JvmField
  protected val view: MutableList<PluginUiModel> = mutableListOf()
  protected val enabledMap: MutableMap<PluginId, PluginEnabledState?> = mutableMapOf()
  private val sessionInitializedDeferred = CompletableDeferred<Unit>()
  private val modificationTracker = getModificationTracker()

  @JvmField
  protected val myInstalledPluginComponents: MutableList<ListPluginComponent> = mutableListOf()
  @JvmField
  protected val myInstalledPluginComponentMap: MutableMap<PluginId?, MutableList<ListPluginComponent>> = mutableMapOf()
  @JvmField
  protected val myMarketplacePluginComponentMap: MutableMap<PluginId?, MutableList<ListPluginComponent>> = mutableMapOf()
  @JvmField
  protected val myDetailPanels: MutableList<PluginDetailsPageComponent> = mutableListOf()
  @JvmField
  protected val myEnabledGroups: MutableList<PluginsGroup> = ArrayList<PluginsGroup>()

  init {
    if (initSessionResult != null) {
      sessionInitializedDeferred.complete(Unit)
    }
    else {
      if (Registry.`is`("reworked.plugin.manager.enabled", false)) {
        coroutineScope.launch(Dispatchers.IO) {
          val pluginManager = UiPluginManager.getInstance()
          initSessionPlugins(pluginManager.initSession(mySessionId),
                             pluginManager.updatePluginDependencies(mySessionId.toString()))
        }
      }
      else {
        val sessionResult = DefaultUiPluginManagerController.initSessionSync(mySessionId.toString())
        val pluginsToEnable = DefaultUiPluginManagerController.updatePluginDependenciesSync(mySessionId.toString())
        initSessionPlugins(sessionResult, pluginsToEnable)
      }
    }
  }

  private fun initSessionPlugins(initSessionResult: InitSessionResult, pluginsToEnable: Set<PluginId>) {
    try {
      view.addAll(initSessionResult.getVisiblePluginsList())
      initSessionResult.pluginStates.forEach { (pluginId, pluginState) ->
        enabledMap[pluginId] = when (pluginState) {
          true -> PluginEnabledState.ENABLED
          false -> PluginEnabledState.DISABLED
          null -> null
        }
      }
      setStatesByIds(pluginsToEnable, true)
    }
    finally {
      sessionInitializedDeferred.complete(Unit)
    }
  }

  suspend fun waitForSessionInitialization() {
    sessionInitializedDeferred.await()
  }

  fun isLoaded(pluginId: PluginId): Boolean {
    return isLoaded(pluginId, this.enabledMap)
  }

  open fun isModified(): Boolean {
    return modificationTracker.isModified()
  }

  private fun setEnabled(ideaPluginDescriptor: PluginUiModel) {
    val pluginId = ideaPluginDescriptor.pluginId
    val enabled = if (ideaPluginDescriptor.isEnabled) PluginEnabledState.ENABLED else PluginEnabledState.DISABLED

    setEnabled(pluginId, enabled)
  }

  private fun getModificationTracker(): SessionModificationTracker {
    if (Registry.`is`("reworked.plugin.manager.enabled", false)) {
      return AsyncSessionModificationTracker(mySessionId.toString(), coroutineScope)
    }
    else {
      return SyncSessionModificationTracker(mySessionId.toString())
    }
  }


  @ApiStatus.NonExtendable
  protected open fun setEnabled(
    pluginId: PluginId,
    enabled: PluginEnabledState?,
  ) {
    enabledMap[pluginId] = enabled
  }

  protected open fun setStatesByIds(ids: Set<PluginId>, enabled: Boolean) {
    val newState = if (enabled) PluginEnabledState.ENABLED else PluginEnabledState.DISABLED
    ids.forEach(Consumer { id: PluginId -> setEnabled(id, newState) })
    updateAfterEnableDisable()
  }

  protected open fun updateAfterEnableDisable() {
    for (component in myInstalledPluginComponents) {
      component.updateEnabledState()
    }
    for (plugins in myMarketplacePluginComponentMap.values) {
      for (plugin in plugins) {
        if (plugin.installedDescriptorForMarketplace != null) {
          plugin.updateEnabledState()
        }
      }
    }
    for (detailPanel in myDetailPanels) {
      detailPanel.updateEnabledState()
    }
  }

  companion object {
    @ApiStatus.Internal
    val HIDE_IMPLEMENTATION_DETAILS: Boolean = !ApplicationManagerEx.isInIntegrationTest()

    @JvmStatic
    protected fun isEnabled(
      pluginId: PluginId,
      enabledMap: MutableMap<PluginId, PluginEnabledState?>,
    ): Boolean {
      val state = enabledMap[pluginId]
      return state?.isEnabled != false
    }

    @ApiStatus.Internal
    fun isDisabled(
      pluginId: PluginId,
      enabledMap: MutableMap<PluginId, PluginEnabledState?>,
    ): Boolean {
      val state = enabledMap[pluginId]
      return state?.isDisabled ?: true
    }

    protected fun isLoaded(
      pluginId: PluginId,
      enabledMap: MutableMap<PluginId, PluginEnabledState?>,
    ): Boolean {
      return pluginId in enabledMap
    }

    @JvmStatic
    protected fun isDeleted(descriptor: IdeaPluginDescriptor): Boolean {
      return descriptor is IdeaPluginDescriptorImpl && descriptor.isDeleted
    }

    @ApiStatus.Internal
    fun isHiddenImplementationDetail(descriptor: IdeaPluginDescriptor): Boolean {
      return HIDE_IMPLEMENTATION_DETAILS && descriptor.isImplementationDetail
    }

    @ApiStatus.Internal
    fun isHidden(descriptor: IdeaPluginDescriptor): Boolean {
      return isDeleted(descriptor) ||
             isHiddenImplementationDetail(descriptor)
    }

    @ApiStatus.Internal
    fun getPluginNameOrId(
      pluginId: PluginId,
      descriptor: IdeaPluginDescriptor?,
    ): @NonNls String {
      return descriptor?.name ?: pluginId.idString
    }
  }

  interface SessionModificationTracker {
    fun isModified(): Boolean
  }

  class AsyncSessionModificationTracker(private val sessionId: String, private val coroutineScope: CoroutineScope) : SessionModificationTracker {
    @Volatile
    private var isModified = false


    override fun isModified(): Boolean {
      //won't cause infinite loop because of isModified != modified check
      scheduleCalculateAndUpdate()
      return isModified
    }

    private fun scheduleCalculateAndUpdate() {
      coroutineScope.launch(Dispatchers.IO) {
        val modified = UiPluginManager.getInstance().isModified(sessionId)
        if (isModified != modified) {
          isModified = modified
          withContext(Dispatchers.EDT + ModalityState.any().asContextElement()) {
            DataManager.getInstance().dataContextFromFocusAsync.then {
              Settings.KEY.getData(it)?.revalidate()
            }
          }
        }
      }
    }
  }

  class SyncSessionModificationTracker(private val sessionId: String) : SessionModificationTracker {
    @Suppress("RAW_RUN_BLOCKING") //will take little time for old implementation, it was always blocking before
    override fun isModified(): Boolean {
      return runBlocking { UiPluginManager.getInstance().isModified(sessionId) }
    }
  }
}