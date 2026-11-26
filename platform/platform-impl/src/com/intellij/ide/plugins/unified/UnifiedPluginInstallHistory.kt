// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.plugins.unified

import com.intellij.openapi.components.Service
import com.intellij.openapi.extensions.PluginId

/** Keeps install groups and their scheduling order for one IDE run. */
@Service(Service.Level.APP)
internal class UnifiedPluginInstallHistory {
  private val lock = Any()
  private val groups = HashMap<Long, SessionPluginInstallGroup>()
  private var nextOrder = 0L

  fun nextOperationOrder(): Long = synchronized(lock) { ++nextOrder }

  fun remember(order: Long, group: SessionPluginInstallGroup) {
    synchronized(lock) {
      if (group.pluginIds.isEmpty()) groups.remove(order)
      else groups[order] = group
    }
  }

  fun forget(pluginIds: Set<PluginId>) {
    synchronized(lock) {
      groups.replaceAll { _, group -> group.copy(pluginIds = group.pluginIds.filterNot(pluginIds::contains)) }
      groups.values.removeIf { it.pluginIds.isEmpty() }
    }
  }

  fun snapshot(): Map<Long, SessionPluginInstallGroup> = synchronized(lock) { groups.toMap() }
}
