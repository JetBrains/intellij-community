// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.plugins.newui

import com.intellij.ide.plugins.DynamicPluginEnabler
import com.intellij.ide.plugins.IdeaPluginDescriptor
import com.intellij.ide.plugins.PluginEnableStateChangedListener
import com.intellij.ide.plugins.PluginStateListener
import com.intellij.ide.plugins.PluginStateManager
import com.intellij.ide.plugins.api.PluginDto
import com.intellij.openapi.diagnostic.getOrHandleException
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.extensions.PluginId
import com.intellij.openapi.updateSettings.impl.PluginUpdateHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.ApiStatus
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Duration.Companion.milliseconds

@OptIn(FlowPreview::class)
@ApiStatus.Internal
class DefaultPluginUpdatesProvider(private val coroutineScope: CoroutineScope) : PluginUpdatesProvider {
  private val updateMutex = Mutex()
  private val flow = MutableStateFlow<PluginUpdatesEvent?>(null)
  private val updateRequestFlow = MutableSharedFlow<Unit>(replay = 1, extraBufferCapacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)
  private var lastPluginUpdates: PluginUpdatesEvent? = null

  companion object {
    private val LOG = logger<DefaultPluginUpdatesProvider>()
  }

  init {
    PluginStateManager.addStateListener(object : PluginStateListener {
      override fun install(descriptor: IdeaPluginDescriptor) {
        coroutineScope.launch { dropFromLastPluginUpdates(setOf(descriptor.getPluginId())) }
      }

      override fun uninstall(descriptor: IdeaPluginDescriptor) {
        coroutineScope.launch { dropFromLastPluginUpdates(setOf(descriptor.getPluginId())) }
      }
    })
    DynamicPluginEnabler.addPluginStateChangedListener( object : PluginEnableStateChangedListener {
      override fun stateChanged(
        pluginDescriptors: Collection<IdeaPluginDescriptor>,
        enable: Boolean,
      ) {
        if (enable) {
          coroutineScope.launch { update() }
          return
        }
        coroutineScope.launch { dropFromLastPluginUpdates(pluginDescriptors.map { it.getPluginId() }.toSet()) }
      }
    })

    collectUpdateRequests()
  }

  private fun collectUpdateRequests() = coroutineScope.launch(Dispatchers.IO) {
    updateRequestFlow
      .debounce(300.milliseconds)
      .collectLatest {
        updateMutex.withLock {
          runCatching {
            val model = (PluginUpdateHandler.getInstance().loadAndStorePluginUpdates(null))
            val pluginUpdates = PluginUpdatesEvent(model.pluginUpdates.markLocal(),
                                                    model.disabledPluginUpdates.markLocal(),
                                                    model.updatesFromCustomRepositories.markLocal())
            lastPluginUpdates = pluginUpdates
            emitUpdates(pluginUpdates)
          }.getOrHandleException { e -> LOG.warn("Failed to load plugin updates:", e) }
        }
      }
  }

  override suspend fun pluginUpdateEvents(): Flow<PluginUpdatesEvent?> {
    return flow
  }

  override suspend fun update() {
    updateRequestFlow.emit(Unit)
  }

  private suspend fun emitUpdates(updates: PluginUpdatesEvent) {
    flow.emit(updates)
  }

  private suspend fun dropFromLastPluginUpdates(pluginUpdatesToRemove: Set<PluginId>) {
    updateMutex.withLock {
      if (lastPluginUpdates == null) return
      computeUpdatesWithout(pluginUpdatesToRemove, lastPluginUpdates!!)?.also {
        lastPluginUpdates = it
        emitUpdates(it)
      }
    }
  }

  private fun computeUpdatesWithout(pluginIds: Set<PluginId>, updates: PluginUpdatesEvent): PluginUpdatesEvent? {
    if (updates.all.none { it.pluginId in pluginIds }) {
      return null
    }
    return PluginUpdatesEvent(
      updates.enabledUpdates.filter { it.pluginId !in pluginIds },
      updates.disabledUpdates.filter { it.pluginId !in pluginIds },
      updates.pluginNods
    )
  }

  private fun List<PluginDto>.markLocal(): List<PluginDto> = onEach { it.source = PluginSource.LOCAL }
}
