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

@Service(Service.Level.APP)
@State(name = "PluginFeatureService", storages = [Storage(StoragePathMacros.CACHE_FILE)])
class PluginFeatureService : SerializablePersistentStateComponent<PluginFeatureService.State>(State()) {
  companion object {
    @JvmStatic
    val instance: PluginFeatureService
      get() = ApplicationManager.getApplication().getService(PluginFeatureService::class.java)
  }

  private val tracker = SimpleModificationTracker()

  @Serializable
  data class FeaturePluginList(val featureMap: MutableMap<String, FeaturePluginData> = HashMap())

  @Serializable
  data class State(val features: MutableMap<String, FeaturePluginList> = HashMap())

  override fun getStateModificationCount() = tracker.modificationCount

  private fun getOrCreateFeature(featureType: String): FeaturePluginList {
    return state.features.computeIfAbsent(featureType) {
      tracker.incModificationCount()
      FeaturePluginList()
    }
  }

  fun <T> collectFeatureMapping(
    featureType: String,
    ep: ExtensionPointName<T>,
    idMapping: (T) -> String,
    displayNameMapping: (T) -> String,
  ) {
    val pluginList = getOrCreateFeature(featureType)
    var changed = false
    ep.processWithPluginDescriptor { ext, descriptor ->
      val pluginData = FeaturePluginData(
        displayNameMapping(ext),
        PluginData(descriptor),
      )
      if (pluginList.featureMap.put(idMapping(ext), pluginData) != pluginData) {
        changed = true
      }
    }

    if (changed) {
      tracker.incModificationCount()
    }
  }

  fun getPluginForFeature(featureType: String, implementationName: String): FeaturePluginData? {
    return state.features.get(featureType)?.featureMap?.get(implementationName)
  }
}
