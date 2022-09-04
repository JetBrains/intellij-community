// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ReplaceGetOrSet")

package com.intellij.ide.plugins

import com.intellij.ide.plugins.advertiser.FeaturePluginData
import com.intellij.ide.plugins.advertiser.PluginData
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.*
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.util.SimpleModificationTracker
import kotlinx.serialization.Serializable
import java.util.concurrent.locks.ReentrantReadWriteLock

@Service(Service.Level.APP)
@State(name = "PluginFeatureService", storages = [Storage(StoragePathMacros.CACHE_FILE)])
class PluginFeatureService : SerializablePersistentStateComponent<PluginFeatureService.State>(State()) {

  companion object {
    @JvmStatic
    val instance: PluginFeatureService
      get() = ApplicationManager.getApplication().getService(PluginFeatureService::class.java)
  }

  private val readWriteLock = ReentrantReadWriteLock()
  private val tracker = SimpleModificationTracker()

  @Serializable
  data class FeaturePluginList(
    val featureMap: Map<String, FeaturePluginData> = emptyMap(),
  )

  @Serializable
  data class State(
    val features: Map<String, FeaturePluginList> = emptyMap(),
  )

  override fun getStateModificationCount() = tracker.modificationCount

  fun <T> collectFeatureMapping(
    featureType: String,
    ep: ExtensionPointName<T>,
    idMapping: (T) -> String,
    displayNameMapping: (T) -> String,
  ) {
    readWriteLock.writeLock().lock()
    try {
      var featureList = state.features.get(featureType)
                        ?: FeaturePluginList()

      // fold
      ep.processWithPluginDescriptor { ext, descriptor ->
        val pluginData = FeaturePluginData(
          displayNameMapping(ext),
          PluginData(descriptor),
        )

        val id = idMapping(ext)
        featureList = FeaturePluginList(featureList.featureMap + (id to pluginData))
      }

      loadState(State(state.features + (featureType to featureList)))
    }
    finally {
      tracker.incModificationCount()
      readWriteLock.writeLock().unlock()
    }
  }

  fun getPluginForFeature(
    featureType: String,
    implementationName: String,
  ): FeaturePluginData? {
    return if (readWriteLock.readLock().tryLock()) {
      try {
        state.features
          .get(featureType)
          ?.featureMap
          ?.get(implementationName)
      }
      finally {
        readWriteLock.readLock().unlock()
      }
    }
    else {
      null
    }
  }
}
