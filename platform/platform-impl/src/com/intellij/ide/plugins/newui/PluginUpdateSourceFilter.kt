// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.plugins.newui

import com.intellij.openapi.extensions.PluginId
import com.intellij.openapi.updateSettings.impl.PluginUpdateSource
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update

internal class PluginUpdateSourceFilter {
  private val changes = MutableStateFlow<Map<PluginId, PluginUpdateSourceChangedState>>(emptyMap())

  fun sourceChanged(pluginId: PluginId, previousSource: PluginUpdateSource?, source: PluginUpdateSource?) {
    changes.update { current ->
      val initial = current[pluginId]?.initialValue ?: PluginUpdateSourceState(previousSource)
      val selected = PluginUpdateSourceState(source)
      if (initial == selected) current - pluginId
      else current + (pluginId to PluginUpdateSourceChangedState(initial, selected))
    }
  }

  fun sourcePersisted(pluginId: PluginId) {
    changes.update { it - pluginId }
  }

  fun clear() {
    changes.value = emptyMap()
  }

  fun filter(updates: Flow<PluginUpdatesEvent>): Flow<PluginUpdatesEvent> {
    return updates.combine(changes) { snapshot, pendingChanges ->
      if (pendingChanges.isEmpty()) snapshot
      else snapshot.copy(
        enabledUpdates = snapshot.enabledUpdates.filterNot { it.pluginId in pendingChanges },
        disabledUpdates = snapshot.disabledUpdates.filterNot { it.pluginId in pendingChanges },
      )
    }
  }
}
