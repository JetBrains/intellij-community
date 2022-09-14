// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.plugins

import com.intellij.ide.feedback.kotlinRejecters.state.KotlinRejectersInfoService
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
    enable(descriptors, project = null)

  fun enable(
    descriptors: Collection<IdeaPluginDescriptor>,
    project: Project? = null,
  ): Boolean {
    PluginManagerUsageCollector.pluginsStateChanged(descriptors, enable = true, project)

    PluginEnabler.HEADLESS.enable(descriptors)
    return DynamicPlugins.loadPlugins(descriptors)
  }

  override fun disable(descriptors: Collection<IdeaPluginDescriptor>): Boolean =
    disable(descriptors, project = null)

  fun disable(
    descriptors: Collection<IdeaPluginDescriptor>,
    project: Project? = null,
    parentComponent: JComponent? = null,
  ): Boolean {
    PluginManagerUsageCollector.pluginsStateChanged(descriptors, enable = false, project)

    recordKotlinPluginDisabling(descriptors)

    PluginEnabler.HEADLESS.disable(descriptors)
    return DynamicPlugins.unloadPlugins(descriptors, project, parentComponent)
  }

  private fun recordKotlinPluginDisabling(descriptors: Collection<IdeaPluginDescriptor>) {
    // Kotlin Plugin + 4 plugin dependency
    if (descriptors.size <= 5
        && descriptors.any { it.pluginId.idString == "org.jetbrains.kotlin" }) {
      KotlinRejectersInfoService.getInstance().state.showNotificationAfterRestart = true
    }
  }
}