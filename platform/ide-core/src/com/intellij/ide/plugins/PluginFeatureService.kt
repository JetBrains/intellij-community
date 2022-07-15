// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ReplaceGetOrSet", "ReplacePutWithAssignment")

package com.intellij.ide.plugins

import com.intellij.ide.plugins.advertiser.FeaturePluginData
import com.intellij.ide.plugins.advertiser.PluginData
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.*
import com.intellij.openapi.extensions.ExtensionPointName
import kotlinx.serialization.Serializable

@Service(Service.Level.APP)
@State(name = "PluginFeatureService", storages = [Storage(StoragePathMacros.CACHE_FILE)])
class PluginFeatureService : SerializablePersistentStateComponent<PluginFeatureService.State>(State()) {

  companion object {
    @JvmStatic
    val instance: PluginFeatureService
      get() = ApplicationManager.getApplication().getService(PluginFeatureService::class.java)
  }

  @Serializable
  data class FeaturePluginList(
    val featureMap: Map<String, FeaturePluginData> = emptyMap(),
  )

  @Serializable
  data class State(
    val features: Map<String, FeaturePluginList> = emptyMap(),
  )

  fun <T> collectFeatureMapping(
    featureType: String,
    ep: ExtensionPointName<T>,
    idMapping: (T) -> String,
    displayNameMapping: (T) -> String,
  ) {
    val featureMap = LinkedHashMap(featureMap(featureType))

    // fold
    ep.processWithPluginDescriptor { ext, descriptor ->
      val pluginData = FeaturePluginData(
        displayNameMapping(ext),
        PluginData(descriptor),
      )

      featureMap.put(idMapping(ext), pluginData)
    }

    updateState { oldState ->
      State(oldState.features + (featureType to FeaturePluginList(featureMap)))
    }
  }

  fun getPluginForFeature(
    featureType: String,
    implementationName: String,
  ): FeaturePluginData? = featureMap(featureType).get(implementationName)

  private fun featureMap(featureType: String) = state.features.get(featureType)?.featureMap ?: emptyMap()
}
