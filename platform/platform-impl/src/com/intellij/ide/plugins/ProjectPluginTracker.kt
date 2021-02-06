// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.plugins

import com.intellij.openapi.components.BaseState
import com.intellij.openapi.extensions.PluginId
import com.intellij.util.xmlb.annotations.XCollection
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
class ProjectPluginTracker : BaseState() {

  var projectName: String = ""
    internal set

  @get:XCollection(propertyElementName = "enabledPlugins", style = XCollection.Style.v2)
  internal val enabledPlugins by stringSet()

  @get:XCollection(propertyElementName = "disabledPlugins", style = XCollection.Style.v2)
  internal val disabledPlugins by stringSet()

  val enabledPluginsIds: Set<PluginId> get() = enabledPlugins.toPluginIds()

  val disabledPluginsIds: Set<PluginId> get() = disabledPlugins.toPluginIds()

  fun isEnabled(pluginId: PluginId) = enabledPlugins.contains(pluginId.idString)

  fun isDisabled(pluginId: PluginId) = disabledPlugins.contains(pluginId.idString)

  fun startTracking(
    pluginIds: Iterable<PluginId>,
    enable: Boolean,
  ): Boolean {
    val (setToRemoveFrom, setToAddTo) = if (enable)
      Pair(disabledPlugins, enabledPlugins)
    else
      Pair(enabledPlugins, disabledPlugins)

    var updated = false

    pluginIds
      .map { it.idString }
      .forEach {
        setToRemoveFrom.remove(it)
        setToAddTo.add(it)
        updated = true
      }

    if (updated) incrementModificationCount()
    return updated
  }

  fun stopTracking(pluginIds: Iterable<PluginId>): Boolean {
    var updated = false

    pluginIds
      .map { it.idString }
      .filterNot {
        val removed = enabledPlugins.remove(it)
        updated = removed || updated
        removed
      }.forEach {
        disabledPlugins.remove(it)
        updated = true
      }

    if (updated) incrementModificationCount()
    return updated
  }

  private fun Set<String>.toPluginIds() = mapNotNullTo(HashSet()) { PluginId.findId(it) }
}