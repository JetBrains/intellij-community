// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ReplaceGetOrSet", "ReplacePutWithAssignment")

package com.intellij.ide.plugins

import com.intellij.ide.plugins.advertiser.FeaturePluginData
import com.intellij.ide.plugins.advertiser.PluginData
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.*
import com.intellij.openapi.extensions.ExtensionPointName
import kotlinx.serialization.Serializable
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.Nls
import org.jetbrains.annotations.NonNls

@ApiStatus.Internal
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
    val featureMap: Map<@NonNls String, FeaturePluginData> = emptyMap(),
  )

  @Serializable
  data class State(
    val features: Map<@NonNls String, FeaturePluginList> = emptyMap(),
  )

  fun <T : Any> collectFeatureMapping(
    featureType: @NonNls String,
    ep: ExtensionPointName<T>,
    idMapping: (T) -> @NonNls String,
    displayNameMapping: (T) -> @Nls String,
  ) {
    val featureMap = LinkedHashMap<@NonNls String, FeaturePluginData>()

    // fold
    ep.processWithPluginDescriptor { ext, descriptor ->
      val pluginData = FeaturePluginData(
        displayNameMapping(ext),
        PluginData(descriptor),
      )

      featureMap.put(idMapping(ext), pluginData)
    }

    updateFeatureMapping(featureType, featureMap)
  }

  @ApiStatus.Experimental
  fun updateFeatureMapping(
    featureType: @NonNls String,
    featureMap: Map<@NonNls String, FeaturePluginData>,
  ) {
    updateState { oldState ->
      val oldFeatures = oldState.features
      val newFeatureMap = LinkedHashMap(oldFeatures.getFeatureMap(featureType) ?: emptyMap()) + featureMap
      State(oldFeatures + (featureType to FeaturePluginList(newFeatureMap)))
    }
  }

  fun getPluginForFeature(
    featureType: @NonNls String,
    implementationName: @NonNls String,
  ): FeaturePluginData? = state.features.getFeatureMap(featureType)?.get(implementationName)

  private fun Map<@NonNls String, FeaturePluginList>.getFeatureMap(featureType: @NonNls String) =
    get(featureType)?.featureMap
}
