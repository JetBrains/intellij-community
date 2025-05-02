// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ReplacePutWithAssignment", "ReplaceGetOrSet")

package com.intellij.openapi.application

import com.intellij.diagnostic.LoadingState
import com.intellij.ide.util.PropertiesComponent
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.components.serviceAsync
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.extensions.ExtensionPointName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.jetbrains.annotations.NonNls
import java.util.concurrent.ConcurrentHashMap

private val LOG = logger<Experiments>()

// used in tests where mock app is used - do not remove the empty constructor
@Service
class Experiments @JvmOverloads constructor(coroutineScope: CoroutineScope? = null) {
  private val cache = ConcurrentHashMap<String, Boolean>()

  companion object {
    @JvmField
    val EP_NAME: ExtensionPointName<ExperimentalFeature> = ExtensionPointName("com.intellij.experimentalFeature")

    @JvmStatic
    fun getInstance(): Experiments = ApplicationManager.getApplication().service()
  }

  init {
    // log enabled experimental features
    coroutineScope?.launch {
      val enabledIds = ArrayList<String>()
      val propertyManager = ApplicationManager.getApplication().serviceAsync<PropertiesComponent>()
      for (feature in EP_NAME.extensionList) {
        var result = cache.get(feature.id)
        if (result == null) {
          result = calcIsFeatureEnabled(feature, propertyManager)
          cache.put(feature.id, result)
        }
        if (result) {
          enabledIds.add(feature.id)
        }
      }
      if (!enabledIds.isEmpty()) {
        LOG.info("Experimental features enabled for user: ${enabledIds.joinToString(separator = ", ")}")
      }
    }
  }

  fun isFeatureEnabled(featureId: @NonNls String): Boolean {
    if (!LoadingState.COMPONENTS_REGISTERED.isOccurred) {
      return false
    }

    var result = cache.get(featureId)
    if (result == null) {
      val feature = getFeatureById(featureId)
      result = feature != null && calcIsFeatureEnabled(feature, PropertiesComponent.getInstance())
      cache.put(featureId, result)
    }
    return result
  }

  fun setFeatureEnabled(featureId: String, enabled: Boolean) {
    val feature = getFeatureById(featureId) ?: return
    cache.put(featureId, enabled)
    val key = toPropertyKey(feature)
    PropertiesComponent.getInstance().setValue(key, enabled, feature.isEnabled())
    LOG.info("Experimental feature '$featureId' is now turned ${if (enabled) "ON" else "OFF"}")
  }

  fun isChanged(featureId: String): Boolean {
    val feature = getFeatureById(featureId)
    return feature != null && feature.isEnabled() != isFeatureEnabled(featureId)
  }
}

private fun calcIsFeatureEnabled(feature: ExperimentalFeature, propertyManager: PropertiesComponent): Boolean {
  val manualOptionIdText = System.getProperty("platform.experiment.ab.manual.option", "")
  if ("control.option".equals(manualOptionIdText, ignoreCase = true)) {
    return false
  }

  val key = toPropertyKey(feature)
  return if (propertyManager.isValueSet(key)) propertyManager.getBoolean(key, false) else feature.isEnabled()
}

private fun getFeatureById(featureId: String): ExperimentalFeature? {
  if (LoadingState.COMPONENTS_REGISTERED.isOccurred) {
    return Experiments.EP_NAME.findFirstSafe { it.id == featureId }
  }
  else {
    return null
  }
}

private fun toPropertyKey(feature: ExperimentalFeature): String = "experimentalFeature.${feature.id}"
