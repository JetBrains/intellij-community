// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.plugins

import com.intellij.ide.plugins.advertiser.FeaturePluginData
import com.intellij.ide.plugins.advertiser.PluginData
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.*
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.util.xmlb.annotations.Tag
import com.intellij.util.xmlb.annotations.XMap

@Service(Service.Level.APP)
@State(
  name = "PluginFeatureService",
  storages = [Storage(StoragePathMacros.CACHE_FILE, roamingType = RoamingType.DISABLED)],
)
class PluginFeatureService : SimplePersistentStateComponent<PluginFeatureService.State>(State()) {

  private val featureMappingsCollected = mutableSetOf<String>()

  @Tag("features")
  class FeaturePluginsList : BaseState() {

    @get:XMap
    val featureMap by linkedMap<String, FeaturePluginData>()

    operator fun set(implementationName: String, pluginData: FeaturePluginData) {
      if (featureMap.put(implementationName, pluginData) != pluginData) {
        incrementModificationCount()
      }
    }

    operator fun get(implementationName: String): FeaturePluginData? = featureMap[implementationName]
  }

  @Tag("pluginFeatures")
  class State : BaseState() {

    @get:XMap
    val features by linkedMap<String, FeaturePluginsList>()

    operator fun set(featureType: String, pluginsList: FeaturePluginsList) {
      if (features.put(featureType, pluginsList) != pluginsList) {
        incrementModificationCount()
      }
    }

    operator fun get(featureType: String): FeaturePluginsList {
      return features.getOrPut(featureType) {
        incrementModificationCount()
        FeaturePluginsList()
      }
    }
  }

  companion object {
    @JvmStatic
    val instance: PluginFeatureService
      get() = ApplicationManager.getApplication().getService(PluginFeatureService::class.java)
  }

  fun <T> collectFeatureMapping(
    featureType: String,
    ep: ExtensionPointName<T>,
    idMapping: (T) -> String,
    displayNameMapping: (T) -> String,
  ) {
    if (!featureMappingsCollected.add(featureType)) return

    val pluginsList = state[featureType]
    ep.processWithPluginDescriptor { ext, descriptor ->
      pluginsList[idMapping(ext)] = FeaturePluginData(
        displayNameMapping(ext),
        PluginData(descriptor),
      )
    }
  }

  fun getPluginForFeature(featureType: String, implementationName: String): FeaturePluginData? {
    return state[featureType].let { pluginsList ->
      pluginsList[implementationName]
    }
  }
}
