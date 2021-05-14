// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.plugins.advertiser

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.*
import com.intellij.util.xmlb.annotations.Property
import org.jetbrains.annotations.ApiStatus
import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.concurrent.read
import kotlin.concurrent.write

@Service(Service.Level.APP)
@State(
  name = "PluginFeatureCacheService",
  storages = [Storage(StoragePathMacros.CACHE_FILE, roamingType = RoamingType.DISABLED)],
  allowLoadInTests = true,
)
@ApiStatus.Internal
class PluginFeatureCacheService : SimplePersistentStateComponent<PluginFeatureCacheService.State>(State()) {

  class State : BaseState() {

    @get:Property
    var extensions by property<PluginFeatureMap?>(null) { it == null }

    @get:Property
    var dependencies by property<PluginFeatureMap?>(null) { it == null }
  }

  companion object {

    private val lock = ReentrantReadWriteLock()

    @JvmStatic
    val instance: PluginFeatureCacheService
      get() = ApplicationManager.getApplication().getService(PluginFeatureCacheService::class.java)
  }

  var extensions: PluginFeatureMap?
    get() = lock.read { state.extensions }
    set(value) = lock.write { state.extensions = value }

  var dependencies: PluginFeatureMap?
    get() = lock.read { state.dependencies }
    set(value) = lock.write { state.dependencies = value }

  override fun loadState(state: State) = lock.write { super.loadState(state) }
}