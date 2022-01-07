// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.plugins.advertiser

import com.intellij.openapi.components.*
import org.jetbrains.annotations.ApiStatus

@Service(Service.Level.APP)
@State(name = "PluginFeatureCacheService", storages = [Storage(StoragePathMacros.CACHE_FILE)], allowLoadInTests = true)
@ApiStatus.Internal
class PluginFeatureCacheService : PersistentStateComponentWithModificationTracker<PluginFeatureCacheService.MyState> {
  @Volatile
  private var state = MyState()

  override fun getState() = state

  override fun getStateModificationCount(): Long {
    val state = state
    return (state.extensions?.modificationCount ?: 0) + (state.dependencies?.modificationCount ?: 0)
  }

  override fun loadState(state: MyState) {
    this.state = state
  }

  class MyState {
    @JvmField
    var extensions: PluginFeatureMap? = null
    @JvmField
    var dependencies: PluginFeatureMap? = null
  }

  companion object {
    @JvmStatic
    fun getInstance(): PluginFeatureCacheService = service()
  }

  var extensions: PluginFeatureMap?
    get() = state.extensions
    set(value) {
      state = MyState().apply {
        extensions = value
        dependencies = state.dependencies
      }
    }

  var dependencies: PluginFeatureMap?
    get() = state.dependencies
    set(value) {
      state = MyState().apply {
        extensions = state.extensions
        dependencies = value
      }
    }
}
