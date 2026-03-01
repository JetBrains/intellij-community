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

    private const val AI_ASSISTANT_PLUGIN_ID = "com.intellij.ml.llm"

    private var testOverride: AiDataCollectionExternalSettings? = null

    @TestOnly
    @JvmStatic
    fun overrideForTest(settings: AiDataCollectionExternalSettings, parentDisposable: Disposable) {
      testOverride = settings
      Disposer.register(parentDisposable) { testOverride = null }
    }

    @JvmStatic
    fun findSettingsImplementedByAiAssistant(): AiDataCollectionExternalSettings? {
      testOverride?.let { return it }
      return EP_NAME.findFirstSafe {
        val pluginInfo = getPluginInfo(it.javaClass)
        pluginInfo.isDevelopedByJetBrains() && pluginInfo.id == AI_ASSISTANT_PLUGIN_ID
      }
    }
  }

  fun isForciblyDisabled(): Boolean

  fun getForciblyDisabledDescription(): String?
}