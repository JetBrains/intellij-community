// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.plugins

import com.intellij.openapi.components.*
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.util.xmlb.annotations.Attribute

@Service
@State(name = "PluginFeatureService", storages = [Storage("pluginAdvertiser.xml")])
class PluginFeatureService : PersistentStateComponent<PluginFeatureService.State> {
  private val featureMappingsCollected = mutableSetOf<String>()

  class FeaturePluginData(
    @Attribute("displayName") val displayName: String = "",
    @Attribute("pluginName") val pluginName: String = "",
    @Attribute("pluginId") val pluginId: String = ""
  )

  class FeaturePluginsList {
    var featureMap = mutableMapOf<String, FeaturePluginData>()
  }

  class State {
    var features = mutableMapOf<String, FeaturePluginsList>()
  }

  companion object {
    @JvmStatic
    fun getInstance(): PluginFeatureService = ServiceManager.getService(PluginFeatureService::class.java)
  }

  private var state = State()

  override fun getState(): State = state

  override fun loadState(state: State) {
    this.state = state
  }

  fun <T> collectFeatureMapping(featureType: String, ep: ExtensionPointName<T>, idMapping: (T) -> String, displayNameMapping: (T) -> String) {
    if (!featureMappingsCollected.add(featureType)) return

    val pluginsList = state.features.getOrPut(featureType) { FeaturePluginsList() }
    ep.processWithPluginDescriptor { ext, pluginDescriptor ->
      val id = idMapping(ext)
      val displayName = displayNameMapping(ext)
      pluginsList.featureMap[id] = FeaturePluginData(
        displayName, pluginDescriptor.name, pluginDescriptor.pluginId.idString)
    }
  }

  fun getPluginForFeature(featureType: String, implementationName: String): FeaturePluginData? {
    val pluginList = state.features[featureType] ?: return null
    return pluginList.featureMap[implementationName] ?: return null
  }
}
