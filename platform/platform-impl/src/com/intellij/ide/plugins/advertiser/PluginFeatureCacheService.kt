// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.ide.plugins.advertiser

import com.intellij.openapi.components.*
import kotlinx.serialization.Serializable
import org.jetbrains.annotations.ApiStatus

/**
 * Caches the marketplace plugins that support given filenames/extensions or dependencies. This data
 * is persisted between IDE restarts and refreshed on startup if the cached data is more than 1 day old
 * (see PluginsAdvertiserStartupActivity.checkSuggestedPlugins). The cached data potentially includes plugins
 * that are incompatible with the current IDE build.
 */
@Service(Service.Level.APP)
@State(name = "PluginFeatureCacheService", storages = [Storage(StoragePathMacros.CACHE_FILE)], allowLoadInTests = true)
@ApiStatus.Internal
class PluginFeatureCacheService : SerializablePersistentStateComponent<PluginFeatureCacheService.MyState>(MyState()) {
  companion object {
    fun getInstance(): PluginFeatureCacheService = service()
  }

  @Serializable
  data class MyState(
    val extensions: PluginFeatureMap? = null,
    val dependencies: PluginFeatureMap? = null
  )

  var extensions: PluginFeatureMap?
    get() = state.extensions
    set(value) {
      updateState {
        MyState(value, it.dependencies)
      }
    }

  var dependencies: PluginFeatureMap?
    get() = state.dependencies
    set(value) {
      updateState {
        MyState(it.extensions, value)
      }
    }
}