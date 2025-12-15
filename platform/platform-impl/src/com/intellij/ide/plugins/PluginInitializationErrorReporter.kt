// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.plugins

import com.intellij.openapi.extensions.PluginId
import com.intellij.openapi.wm.IdeFrame
import com.intellij.openapi.wm.ex.WindowManagerEx
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
object PluginInitializationErrorReporter {
  @Synchronized
  @JvmStatic
  fun onEnable(enabled: Boolean): Boolean {
    val (pluginsToEnable, pluginsToDisable) = PluginManagerCore.consumeStartupActionsPluginsToEnableDisable()
    val pluginIds = if (enabled) pluginsToEnable else pluginsToDisable
    if (pluginIds.isEmpty()) {
      return false
    }
    val descriptors = ArrayList<IdeaPluginDescriptorImpl>()
    for (descriptor in PluginManagerCore.getPluginSet().allPlugins) {
      if (descriptor.getPluginId() in pluginIds) {
        descriptor.isMarkedForLoading = enabled
        descriptors.add(descriptor)
      }
    }
    val pluginEnabler = PluginEnabler.getInstance()
    if (enabled) {
      pluginEnabler.enable(descriptors)
    }
    else {
      pluginEnabler.disable(descriptors)
    }
    return true
  }


  @ApiStatus.Internal
  fun onEvent(description: String) {
    when (description) {
      PluginManagerCore.DISABLE -> onEnable(false)
      PluginManagerCore.ENABLE -> {
        if (onEnable(true)) {
          PluginManagerMain.notifyPluginsUpdated(null)
        }
      }
      PluginManagerCore.EDIT -> {
        val frame: IdeFrame? = WindowManagerEx.getInstanceEx().findFrameFor(null)
        PluginManagerConfigurable.showPluginConfigurable(frame?.getComponent(), null, mutableListOf<PluginId?>())
      }
    }
  }
}