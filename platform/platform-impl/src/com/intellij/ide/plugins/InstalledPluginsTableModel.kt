// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.plugins

import com.intellij.ide.plugins.marketplace.InitSessionResult
import com.intellij.ide.plugins.newui.FrontendRpcCoroutineContext
import com.intellij.ide.plugins.newui.PluginUiModel
import com.intellij.ide.plugins.newui.UiPluginManager.Companion.getInstance
import com.intellij.openapi.application.ex.ApplicationManagerEx
import com.intellij.openapi.components.service
import com.intellij.openapi.extensions.PluginId
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Pair
import kotlinx.coroutines.CoroutineScope
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.NonNls
import java.util.*

@ApiStatus.Internal
open class InstalledPluginsTableModel @JvmOverloads constructor(
  protected val project: Project?,
  initSessionResult: InitSessionResult? = null,
  @JvmField val mySessionId: UUID = UUID.randomUUID(),
) {
  @JvmField
  protected val view: MutableList<PluginUiModel> = mutableListOf()
  protected val enabledMap: MutableMap<PluginId, PluginEnabledState?> = mutableMapOf()
  var coroutineScope: CoroutineScope = service<FrontendRpcCoroutineContext>().coroutineScope

  init {
    val session = initSessionResult ?: getInstance().initSession(mySessionId)
    view.addAll(session.getVisiblePluginsList())
    session.pluginStates.forEach { (pluginId, pluginState) ->
      enabledMap[pluginId] = when (pluginState) {
        true -> PluginEnabledState.ENABLED
        false -> PluginEnabledState.DISABLED
        null -> null
      }
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
    enabled: PluginEnabledState?,
  ) {
    enabledMap[pluginId] = enabled
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
      return state?.isDisabled() ?: true
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
}