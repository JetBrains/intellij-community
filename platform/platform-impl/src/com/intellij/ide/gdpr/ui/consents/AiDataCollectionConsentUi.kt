// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.gdpr.ui.consents

import com.intellij.ide.IdeBundle
import com.intellij.ide.gdpr.Consent
import com.intellij.internal.statistic.utils.getPluginInfo
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.util.text.StringUtil
import org.jetbrains.annotations.ApiStatus

internal class AiDataCollectionConsentUi(private val consent: Consent) : ConsentUi {
  override fun getCheckBoxText(): @NlsSafe String = StringUtil.capitalize(consent.name)

  override fun getCheckBoxCommentText(): @NlsSafe String = """${consent.text}
${IdeBundle.message("gdpr.ai.data.collection.consent.additional.notice.1")}
${IdeBundle.message("gdpr.ai.data.collection.consent.additional.notice.2")}"""

  override fun getForcedState(): ConsentForcedState? {
    val externalSettings = AiDataCollectionExternalSettings.findSettingsImplementedByAiAssistant()
    if (externalSettings != null && externalSettings.isForciblyDisabled()) {
      val description = externalSettings.getForciblyDisabledDescription()
                        ?: IdeBundle.message("gdpr.consent.externally.disabled.warning")
      return ConsentForcedState.ExternallyDisabled(description)
    }
    return null
  }
}

@ApiStatus.Internal
interface AiDataCollectionExternalSettings {
  companion object {
    val EP_NAME: ExtensionPointName<AiDataCollectionExternalSettings> =
      ExtensionPointName.create("com.intellij.aiDataCollectionExternalSettings")

    private const val AI_ASSISTANT_PLUGIN_ID = "com.intellij.ml.llm"

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