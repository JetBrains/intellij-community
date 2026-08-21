// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide

import com.intellij.ide.RegionSettings.RegionSettingsListener
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.components.serviceAsync
import com.intellij.openapi.diagnostic.rethrowControlFlowException
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.progress.util.runWithCheckCanceled
import com.intellij.util.application
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.ApiStatus

/**
 * Provides cached access to the [Region] selected by the user.
 *
 * [RegionSettings.getRegion] reads the value from the OS preferences storage (files, Windows Registry),
 * so it is a slow IO operation that must not be called on EDT or under a read action.
 * This service keeps the last known value in memory, so most callers can get the region without doing IO.
 *
 * The cache is filled right after the service is created (the service is preloaded on startup by
 * `RegionSettingsServiceActivity`) and is refreshed on every [RegionSettings.RegionSettingsListener.UPDATE_TOPIC] event,
 * so a value returned by [getCurrentRegionIfKnown] may be stale for a short time after the user changes the region.
 *
 * @see RegionSettings
 * @see Region
 */
@ApiStatus.Experimental
@Service(Service.Level.APP)
class RegionSettingsService(coroutineScope: CoroutineScope) {

  companion object {
    /**
     * Note that obtaining the service instance does not guarantee that the region is already cached,
     * see [getCurrentRegionIfKnown].
     */
    fun getInstance(): RegionSettingsService = service()

    suspend fun getInstanceAsync(): RegionSettingsService = serviceAsync()
  }

  private val events = MutableSharedFlow<Unit>(replay = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)
  private val cachedRegion = MutableStateFlow<Region?>(null)

  init {
    application.messageBus.connect(coroutineScope).subscribe(RegionSettingsListener.UPDATE_TOPIC, RegionSettingsListener {
      scheduleUpdate()
    })

    scheduleUpdate()

    coroutineScope.launch {
      events.collectLatest {
        try {
          cachedRegion.value = computeRegion()
        }
        catch (e: Exception) {
          rethrowControlFlowException(e)
          thisLogger().error(e)
        }
      }
    }
  }

  private fun scheduleUpdate() {
    events.tryEmit(Unit)
  }

  /**
   * Returns the current region, reading it from the settings storage on [Dispatchers.IO] if it is not cached yet.
   *
   * This is the preferred way to get the region: it never blocks the calling thread and always returns a value,
   * so the caller does not need a fallback for the not-yet-initialized state.
   */
  suspend fun getCurrenRegion(): Region {
    cachedRegion.value?.let { return it }

    return computeRegion()
  }

  /**
   * Returns the current region, blocking the calling thread until it is read from the settings storage
   * if it is not cached yet.
   *
   * Unlike [RegionSettings.getRegion], this method may be called under a read action: the IO happens on another
   * thread, while the calling thread waits and stays cancellable. Must not be called on EDT — use
   * [getCurrentRegionIfKnown] there instead.
   */
  @RequiresBackgroundThread(generateAssertion = false)
  fun getCurrentRegionBlocking(): Region {
    cachedRegion.value?.let { return it }

    return runWithCheckCanceled { computeRegion() }
  }

  private suspend fun computeRegion(): Region {
    return withContext(Dispatchers.IO) { RegionSettings.getRegion() }
  }

  /**
   * Returns the cached region, or `null` if it has not been read yet.
   *
   * The only accessor that is safe to call from EDT, since it never does IO and returns immediately.
   * The caller has to handle the `null` case, normally by falling back to [Region.NOT_SET] or by postponing
   * the region-dependent work.
   */
  fun getCurrentRegionIfKnown(): Region? {
    return cachedRegion.value
  }
}