// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.plugins.newui

import com.intellij.openapi.extensions.PluginId
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
sealed interface PluginModelEvent {
  /** Empty [pluginIds] means that the complete inventory may have changed. */
  data class InventoryInvalidated(
    val reason: PluginInventoryChangeReason,
    val pluginIds: Set<PluginId>,
  ) : PluginModelEvent
}

@ApiStatus.Internal
enum class PluginInventoryChangeReason {
  APPLY,
  RESET,
  INSTALL,
  UPDATE,
  UNINSTALL,
  ENABLE_DISABLE,
  INSTALL_FROM_DISK,
}

/** Receives page-session model events on the thread which completes the corresponding operation. */
@ApiStatus.Internal
fun interface PluginModelEventSink {
  fun onEvent(event: PluginModelEvent)

  companion object {
    @JvmField
    val NONE: PluginModelEventSink = PluginModelEventSink { }
  }
}

internal class PluginModelEventPublisher(private val sink: PluginModelEventSink) {
  fun inventoryInvalidated(
    reason: PluginInventoryChangeReason,
    pluginIds: Collection<PluginId> = emptySet(),
  ) {
    sink.onEvent(PluginModelEvent.InventoryInvalidated(reason, pluginIds.toSet()))
  }
}
