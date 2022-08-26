// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.plugins

import com.intellij.ide.feedback.kotlinRejecters.recordKotlinPluginDisabling
import com.intellij.ide.plugins.marketplace.statistics.PluginManagerUsageCollector
import com.intellij.openapi.extensions.PluginId
import com.intellij.openapi.project.Project
import org.jetbrains.annotations.ApiStatus
import javax.swing.JComponent

@ApiStatus.Internal
internal class DynamicPluginEnabler : PluginEnabler {

  override fun isDisabled(pluginId: PluginId): Boolean =
    PluginEnabler.HEADLESS.isDisabled(pluginId)

  override fun enable(descriptors: Collection<IdeaPluginDescriptor>): Boolean =
    updatePluginsState(descriptors, PluginEnableDisableAction.ENABLE_GLOBALLY)

  override fun disable(descriptors: Collection<IdeaPluginDescriptor>): Boolean =
    updatePluginsState(descriptors, PluginEnableDisableAction.DISABLE_GLOBALLY)

  @ApiStatus.Internal
  @JvmOverloads
  fun updatePluginsState(
    descriptors: Collection<IdeaPluginDescriptor>,
    action: PluginEnableDisableAction,
    project: Project? = null,
    parentComponent: JComponent? = null,
  ): Boolean {
    PluginManagerUsageCollector.pluginsStateChanged(descriptors, action, project)
    recordKotlinPluginDisabling(descriptors, action)

    return when (action) {
      PluginEnableDisableAction.ENABLE_GLOBALLY -> {
        PluginEnabler.HEADLESS.enable(descriptors)
        DynamicPlugins.loadPlugins(descriptors)
      }
      PluginEnableDisableAction.DISABLE_GLOBALLY -> {
        PluginEnabler.HEADLESS.disable(descriptors)
        DynamicPlugins.unloadPlugins(
          descriptors,
          project,
          parentComponent,
        )
      }
    }
  }
}