// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.gdpr.ui.consents

import com.intellij.internal.statistic.utils.getPluginInfo
import com.intellij.openapi.Disposable
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.util.Disposer
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.TestOnly

@ApiStatus.Internal
interface AiDataCollectionExternalSettings {
  companion object {
    val EP_NAME: ExtensionPointName<AiDataCollectionExternalSettings> =
      ExtensionPointName.create("com.intellij.aiDataCollectionExternalSettings")

    /**
     * Plugins allowed to answer for AI data collection, in priority order: AIR wins over AI Assistant, because AIR
     * is where the consent surface ends up. The order is inert today, since AIR registers no extension yet, and it
     * is what decides whose answer counts once it does.
     */
    private val AI_PLUGIN_IDS: List<String> = listOf("com.intellij.air", "com.intellij.ml.llm")

    private var testOverride: AiDataCollectionExternalSettings? = null

    @TestOnly
    @JvmStatic
    fun overrideForTest(settings: AiDataCollectionExternalSettings, parentDisposable: Disposable) {
      testOverride = settings
      Disposer.register(parentDisposable) { testOverride = null }
    }

    @JvmStatic
    fun findSettingsImplementedByAiPlugin(): AiDataCollectionExternalSettings? {
      testOverride?.let { return it }
      for (pluginId in AI_PLUGIN_IDS) {
        val settings = EP_NAME.findFirstSafe {
          val pluginInfo = getPluginInfo(it.javaClass)
          pluginInfo.isDevelopedByJetBrains() && pluginInfo.id == pluginId
        }
        if (settings != null) {
          return settings
        }
      }
      return null
    }
  }

  fun isForciblyDisabled(): Boolean

  fun getForciblyDisabledDescription(): String?
}