// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.plugins.newui

import com.intellij.ide.plugins.api.PluginDto
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.UI
import com.intellij.openapi.application.asContextElement
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.extensions.PluginId
import com.intellij.openapi.progress.runBlockingMaybeCancellable
import com.intellij.util.concurrency.annotations.RequiresEdt
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.VisibleForTesting
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import java.util.function.Consumer
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import kotlin.time.Duration.Companion.milliseconds

@ApiStatus.Internal
fun interface PluginUpdateSubscription {
  fun cancel()
}

typealias PluginUpdateCallback = Consumer<PluginUpdatesEvent>

@ApiStatus.Internal
@Service
@OptIn(FlowPreview::class, ExperimentalAtomicApi::class)
class PluginUpdatesService(val coroutineScope: CoroutineScope) {

  private val myCallbacks = CopyOnWriteArrayList<PluginUpdateCallback>()
  private val pluginUpdateFlow = MutableStateFlow<PluginUpdatesEvent?>(null)
  private val updateIdsFlow = MutableStateFlow<Set<PluginId>?>(null)
  private val updateRequestFlow = MutableSharedFlow<Unit>(replay = 1, extraBufferCapacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)
  private val providerSnapshots = ConcurrentHashMap<PluginUpdatesProvider, PluginUpdatesEvent>()
  private val updateMutex = Mutex()

  companion object {
    private val LOG = logger<PluginUpdatesService>()

    @JvmStatic
    fun getInstance(): PluginUpdatesService = service()

    @JvmStatic
    fun isNeedUpdate(pluginId: PluginId): Boolean {
      return runBlockingMaybeCancellable { getInstance().awaitHasUpdate(pluginId) }
    }

    @JvmStatic
    fun recalculateUpdates() {
      getInstance().recalculateUpdates()
    }
  }

  init {
    startUpdateCollection()
    startUpdateTrigger()
  }

  private suspend fun ensureUpdatesStarted() {
    if (pluginUpdateFlow.value == null) {
      triggerUpdates()
    }
  }

  private fun startUpdateCollection() {
    for (provider in PluginUpdatesProvider.getInstances()) {
      coroutineScope.launch {
        provider.pluginUpdateEvents()
          .catch { e -> LOG.warn("Plugin update provider failed: ${provider.javaClass.name}", e) }
          .collect { event ->
            event?.let {
              providerSnapshots[provider] = event
              onProviderUpdated()
            }
          }
        }
    }
  }

  private suspend fun onProviderUpdated() {
    val merged = updateMutex.withLock {
      val merged = mergeUpdates(providerSnapshots.values)
      pluginUpdateFlow.value = merged
      updateIdsFlow.value = merged.all.mapTo(HashSet()) { plugin -> plugin.pluginId }
      merged
    }
    withContext(Dispatchers.UI + ModalityState.any().asContextElement()) {
      dispatchCallbacks(merged)
    }
  }

  private fun startUpdateTrigger() = coroutineScope.launch {
    updateRequestFlow
      .debounce(300.milliseconds)
      .collect {
        PluginUpdatesProvider.getInstances().forEach { it.update() }
      }
  }

  private suspend fun triggerUpdates() {
    updateRequestFlow.emit(Unit)
  }

  /**
   * Registers a [callback] to receive plugin update events and returns a [PluginUpdateSubscription] to cancel it.
   *
   * By default, the [callback] is invoked on [Dispatchers.UI][com.intellij.openapi.application.UI], WIL not allowed.
   *
   * Note: if an update snapshot is already available, the [callback] is also invoked once synchronously on the
   * calling thread of [subscribe] with that snapshot; all later invocations happen on [Dispatchers.UI].
   */
  @RequiresEdt
  fun subscribe(@RequiresEdt callback: PluginUpdateCallback): PluginUpdateSubscription {
    myCallbacks.add(callback)

    val currentSnapshot = getLastUpdates()
    if (currentSnapshot != null) {
      callback.accept(currentSnapshot)
    } else {
      recalculateUpdates()
    }

    return PluginUpdateSubscription {
      myCallbacks.remove(callback)
    }
  }

  fun recalculateUpdates(): Job = coroutineScope.launch { triggerUpdates() }

  suspend fun awaitUpdates(): Collection<PluginUiModel> {
    ensureUpdatesStarted()
    return pluginUpdateFlow.filterNotNull().first().all
  }

  @VisibleForTesting
  fun flow(): Flow<PluginUpdatesEvent?> = pluginUpdateFlow

  suspend fun awaitHasUpdate(pluginId: PluginId): Boolean {
    ensureUpdatesStarted()
    return updateIdsFlow.filterNotNull().first().contains(pluginId)
  }

  @RequiresEdt
  fun rerunCallbacks() {
    val currentUpdates = getLastUpdates()
    if (currentUpdates != null) {
      dispatchCallbacks(currentUpdates)
    }
  }

  private fun getLastUpdates(): PluginUpdatesEvent? {
    return pluginUpdateFlow.value
  }

  private fun dispatchCallbacks(updates: PluginUpdatesEvent) {
    myCallbacks.forEach { it.accept(updates) }
  }

  @VisibleForTesting
  internal fun mergeUpdates(updateEvents: Collection<PluginUpdatesEvent>): PluginUpdatesEvent =
    PluginUpdatesEvent(mergePlugins(updateEvents) { it.enabledUpdates },
                       mergePlugins(updateEvents) { it.disabledUpdates },
                       mergePlugins(updateEvents) { it.pluginNods })

  private fun mergePlugins(updateEvents: Collection<PluginUpdatesEvent>, updates: (PluginUpdatesEvent) -> List<PluginDto>): List<PluginDto> {
    val merged = LinkedHashMap<PluginId, PluginDto>()
    for (event in updateEvents) {
      for (plugin in updates(event)) {
        val existing = merged[plugin.pluginId]
        if (existing == null) {
          merged[plugin.pluginId] = plugin
        }
        else {
          existing.source = existing.source.addSource(plugin.source)
        }
      }
    }
    return merged.values.toList()
  }
}
