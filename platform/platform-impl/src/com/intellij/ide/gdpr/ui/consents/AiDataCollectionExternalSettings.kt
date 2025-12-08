// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.gdpr.ui.consents

import com.intellij.internal.statistic.utils.getPluginInfo
import com.intellij.openapi.extensions.ExtensionPointName
import org.jetbrains.annotations.ApiStatus
import kotlin.jvm.javaClass

@ApiStatus.Internal
interface AiDataCollectionExternalSettings {
  companion object {
    val EP_NAME: ExtensionPointName<AiDataCollectionExternalSettings> =
      ExtensionPointName.create("com.intellij.aiDataCollectionExternalSettings")

    private const val AI_ASSISTANT_PLUGIN_ID = "com.intellij.ml.llm"

    @JvmStatic
    fun findSettingsImplementedByAiAssistant(): AiDataCollectionExternalSettings? {
      return EP_NAME.findFirstSafe {
        val pluginInfo = getPluginInfo(it.javaClass)
        pluginInfo.isDevelopedByJetBrains() && pluginInfo.id == AI_ASSISTANT_PLUGIN_ID
      }
    }
  }

  fun isForciblyDisabled(): Boolean

  fun getForciblyDisabledDescription(): String?
}