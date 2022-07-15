// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ReplaceGetOrSet", "ReplacePutWithAssignment")

package com.intellij.ide.plugins

import com.intellij.ide.plugins.advertiser.FeaturePluginData
import com.intellij.ide.plugins.advertiser.PluginData
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.*
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.util.SimpleModificationTracker
import kotlinx.serialization.Serializable
import java.util.concurrent.atomic.AtomicReference

@Service(Service.Level.APP)
@State(name = "PluginFeatureService", storages = [Storage(StoragePathMacros.CACHE_FILE)])
class PluginFeatureService : PersistentStateComponentWithModificationTracker<PluginFeatureService.State> {

  companion object {
    @JvmStatic
    val instance: PluginFeatureService
      get() = ApplicationManager.getApplication().getService(PluginFeatureService::class.java)
  }

  private val tracker = SimpleModificationTracker()
  private var state = AtomicReference(State())

  override fun getState(): State = state.get()

  override fun loadState(newState: State) {
    this.state.updateAndGet { oldState ->
      tracker.incModificationCount()
      State(oldState.features + newState.features)
    }
  }

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
    val featureMap = LinkedHashMap(featureMap(featureType))

    // fold
    ep.processWithPluginDescriptor { ext, descriptor ->
      val pluginData = FeaturePluginData(
        displayNameMapping(ext),
        PluginData(descriptor),
      )

      featureMap.put(idMapping(ext), pluginData)
    }

    loadState(State(mapOf(featureType to FeaturePluginList(featureMap))))
  }

  fun getPluginForFeature(
    featureType: String,
    implementationName: String,
  ): FeaturePluginData? = featureMap(featureType).get(implementationName)

  private fun featureMap(featureType: String) = getState().features.get(featureType)?.featureMap ?: emptyMap()
}
