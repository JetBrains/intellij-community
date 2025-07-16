// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.plugins

import com.intellij.ide.plugins.marketplace.InitSessionResult
import com.intellij.ide.plugins.newui.PluginUiModel
import com.intellij.ide.plugins.newui.UiPluginManager.Companion.getInstance
import com.intellij.openapi.application.ex.ApplicationManagerEx
import com.intellij.openapi.extensions.PluginId
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Pair
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.NonNls
import java.util.*
import java.util.function.BiConsumer

open class InstalledPluginsTableModel @ApiStatus.Internal constructor(
  protected val project: Project?,
  initSessionResult: InitSessionResult?,
  @JvmField val mySessionId: UUID
) {
  @JvmField
  protected val view: MutableList<PluginUiModel?> = ArrayList<PluginUiModel?>()
  protected val enabledMap: MutableMap<PluginId?, PluginEnabledState?> = HashMap<PluginId?, PluginEnabledState?>()

  constructor(project: Project?) : this(project, null, UUID.randomUUID())

  init {
    val session = if (initSessionResult == null) getInstance().initSession(mySessionId) else initSessionResult
    view.addAll(session.getVisiblePluginsList())
    session.pluginStates.forEach { (pluginId: PluginId?, pluginState: Boolean?) ->
      enabledMap.put(pluginId,
                     if (pluginState != null) (if (pluginState) PluginEnabledState.ENABLED else PluginEnabledState.DISABLED) else null)
    }
  }

  fun isLoaded(pluginId: PluginId): Boolean {
    return isLoaded(pluginId, this.enabledMap)
  }

  private fun setEnabled(ideaPluginDescriptor: PluginUiModel) {
    val pluginId = ideaPluginDescriptor.pluginId
    val enabled = if (ideaPluginDescriptor.isEnabled) PluginEnabledState.ENABLED else PluginEnabledState.DISABLED

    setEnabled(pluginId, enabled)
  }

  @ApiStatus.NonExtendable
  protected open fun setEnabled(
    pluginId: PluginId,
    enabled: PluginEnabledState?
  ) {
    enabledMap.put(pluginId, enabled)
  }

  protected open fun updatePluginDependencies(pluginIdMap: MutableMap<PluginId?, IdeaPluginDescriptorImpl?>?) {
  }


  protected fun handleBeforeChangeEnableState(
    descriptor: IdeaPluginDescriptor,
    pair: Pair<PluginEnableDisableAction?, PluginEnabledState?>
  ) {
  }

  companion object {
    @ApiStatus.Internal
    val HIDE_IMPLEMENTATION_DETAILS: Boolean = !ApplicationManagerEx.isInIntegrationTest()

    private fun findByPluginId(
      pluginId: PluginId,
      pluginIdMap: MutableMap<PluginId?, IdeaPluginDescriptorImpl?>
    ): IdeaPluginDescriptorImpl {
      return Objects.requireNonNull<IdeaPluginDescriptorImpl>(pluginIdMap.get(pluginId),
                                                              "'" + pluginId + "' not found")
    }

    private fun setNewEnabled(
      descriptors: MutableCollection<PluginUiModel>,
      enabledMap: MutableMap<PluginId?, PluginEnabledState?>,
      action: PluginEnableDisableAction,
      beforeHandler: BiConsumer<PluginUiModel?, in Pair<PluginEnableDisableAction?, PluginEnabledState?>>
    ) {
      for (descriptor in descriptors) {
        val pluginId = descriptor.pluginId
        val oldState = enabledMap.get(pluginId)

        val newState = if (oldState == null) PluginEnabledState.DISABLED else action.apply(oldState)
        if (newState != null) {
          beforeHandler.accept(descriptor, Pair.create<PluginEnableDisableAction?, PluginEnabledState?>(action, newState))
          enabledMap.put(pluginId, newState)
        }
      }
    }

    protected fun isEnabled(
      pluginId: PluginId,
      enabledMap: MutableMap<PluginId?, PluginEnabledState?>
    ): Boolean {
      val state = enabledMap.get(pluginId)
      return state == null || state.isEnabled()
    }

    @ApiStatus.Internal
    fun isDisabled(
      pluginId: PluginId,
      enabledMap: MutableMap<PluginId?, PluginEnabledState?>
    ): Boolean {
      val state = enabledMap.get(pluginId)
      return state == null || state.isDisabled()
    }

    protected fun isLoaded(
      pluginId: PluginId,
      enabledMap: MutableMap<PluginId?, PluginEnabledState?>
    ): Boolean {
      return enabledMap.get(pluginId) != null
    }

    @JvmStatic
    protected fun isDeleted(descriptor: IdeaPluginDescriptor): Boolean {
      return descriptor is IdeaPluginDescriptorImpl && descriptor.isDeleted
    }

    @ApiStatus.Internal
    fun isHiddenImplementationDetail(descriptor: IdeaPluginDescriptor): Boolean {
      return HIDE_IMPLEMENTATION_DETAILS && descriptor.isImplementationDetail()
    }

    @ApiStatus.Internal
    fun isHidden(descriptor: IdeaPluginDescriptor): Boolean {
      return isDeleted(descriptor) ||
             isHiddenImplementationDetail(descriptor)
    }

    @ApiStatus.Internal
    fun getPluginNameOrId(
      pluginId: PluginId,
      descriptor: IdeaPluginDescriptor?
    ): @NonNls String {
      return if (descriptor != null) descriptor.getName() else pluginId.idString
    }
  }
}