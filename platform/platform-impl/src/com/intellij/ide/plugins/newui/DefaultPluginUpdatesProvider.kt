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
import com.intellij.openapi.updateSettings.impl.PluginUpdateSourceListener
import com.intellij.openapi.updateSettings.impl.PluginUpdateSourceService
import com.intellij.openapi.updateSettings.impl.PluginUpdatesModel
import com.intellij.util.application
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.jetbrains.annotations.ApiStatus
import kotlin.time.Duration.Companion.milliseconds

// @spec platform/platform-impl/spec/plugin-manager/plugin-operations.spec.md
@OptIn(FlowPreview::class)
@ApiStatus.Internal
class DefaultPluginUpdatesProvider(private val coroutineScope: CoroutineScope) : PluginUpdatesProvider {
  private val updateMutex = Mutex()
  private val flow = MutableSharedFlow<PluginUpdatesEvent?>(replay = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST).apply {
    tryEmit(null)
  }
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
        coroutineScope.launch {
          updateMutex.withLock {
            val updates = lastPluginUpdates ?: return@withLock
            val updated = updates.withDisabledPlugins(pluginDescriptors.map { it.pluginId }.toSet())
            if (updated !== updates) {
              lastPluginUpdates = updated
              emitUpdates(updated)
            }
          }
        }
      }
    })
    PluginUpdateSourceService.addPluginUpdateSourceFilteringRegistryListener(coroutineScope) {
      coroutineScope.launch { update() }
    }
    application.messageBus.connect(coroutineScope).subscribe(PluginUpdateSourceListener.TOPIC, PluginUpdateSourceListener {
      coroutineScope.launch { update() }
    })
    collectUpdateRequests()
  }

  private fun collectUpdateRequests() = coroutineScope.launch(Dispatchers.IO) {
    updateRequestFlow
      .debounce(300.milliseconds)
      .collectLatest {
        updateMutex.withLock {
          runCatching {
            val model = PluginUpdateHandler.getInstance().loadAndStorePluginUpdates(null)
            publishUpdates(model)
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

  internal suspend fun updatesChecked(model: PluginUpdatesModel) {
    updateMutex.withLock {
      publishUpdates(model)
    }
  }

  private suspend fun emitUpdates(updates: PluginUpdatesEvent) {
    flow.emit(updates)
  }

  private suspend fun publishUpdates(model: PluginUpdatesModel) {
    val pluginUpdates = PluginUpdatesEvent(model.pluginUpdates.markLocalIfUnset(),
                                           model.disabledPluginUpdates.markLocalIfUnset(),
                                           model.updatesFromCustomRepositories.markLocalIfUnset())
    lastPluginUpdates = pluginUpdates
    emitUpdates(pluginUpdates)
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

  /**
   * Sets the source of an update that the handler left unset.
   * The combined handler of the split frontend already sets LOCAL, REMOTE, or BOTH.
   */
  private fun List<PluginDto>.markLocalIfUnset(): List<PluginDto> = onEach {
    if (it.source == null) it.source = PluginSource.LOCAL
  }
}

/** Publishes a completed check for the current IDE build to the plugin manager. */
@ApiStatus.Internal
suspend fun publishPluginUpdates(buildNumber: String?, model: PluginUpdatesModel) {
  if (buildNumber != null) return
  for (provider in PluginUpdatesProvider.getInstances()) {
    if (provider is DefaultPluginUpdatesProvider) {
      provider.updatesChecked(model)
    }
  }
}

internal fun PluginUpdatesEvent.withDisabledPlugins(pluginIds: Set<PluginId>): PluginUpdatesEvent {
  val (disabled, enabled) = enabledUpdates.partition { it.pluginId in pluginIds }
  if (disabled.isEmpty()) return this
  val disabledUpdatesById = LinkedHashMap<PluginId, PluginDto>()
  for (update in disabledUpdates + disabled) {
    val existing = disabledUpdatesById.putIfAbsent(update.pluginId, update)
    if (existing != null) {
      existing.source = existing.source.addSource(update.source)
    }
  }
  return copy(
    enabledUpdates = enabled,
    disabledUpdates = disabledUpdatesById.values.toList(),
  )
}
