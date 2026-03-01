// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.ide.gdpr.ui.consents

import com.intellij.ide.IdeBundle
import com.intellij.ide.gdpr.Consent
import com.intellij.ide.gdpr.DataCollectionAgreement
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.util.text.StringUtil

internal class TraceDataCollectionConsentUI(
  private val consent: Consent,
) : ConsentUi {
  override fun getCheckBoxText(): @NlsSafe String = StringUtil.capitalize(consent.name)

  override fun getCheckBoxCommentText(): @NlsSafe String = consent.text

  override fun getForcedState(): ConsentForcedState? {
    val externalSettings = AiDataCollectionExternalSettings.findSettingsImplementedByAiAssistant()
    if (LOG.isDebugEnabled) {
      LOG.debug("AiDataCollectionExternalSettings: $externalSettings")
    }
    if (externalSettings == null) {
      // AIA plugin is required for TRACE data collection
      return ConsentForcedState.ExternallyDisabled(IdeBundle.message("gdpr.consent.trace.requires.ai.assistant"))
    }
    val isForciblyDisabled = externalSettings.isForciblyDisabled()
    if (LOG.isDebugEnabled) {
      LOG.debug("AiDataCollectionExternalSettings: isForciblyDisabled: $isForciblyDisabled")
    }
    if (isForciblyDisabled) {
      return ConsentForcedState.ExternallyDisabled(externalSettings.getForciblyDisabledDescription()
                                                   ?: IdeBundle.message("gdpr.consent.externally.disabled.warning"))
    }
    val dataCollectionAgreement = DataCollectionAgreement.getInstance()
    return when (dataCollectionAgreement) {
      DataCollectionAgreement.YES -> ConsentForcedState.AlwaysEnabled(IdeBundle.message("gdpr.data.collection.consent.setting.enabled.warning.text"))
      DataCollectionAgreement.NO -> ConsentForcedState.ExternallyDisabled(IdeBundle.message("gdpr.data.collection.consent.setting.disabled.warning.text"))
      DataCollectionAgreement.NOT_SET -> null
      else -> null
    }
  }
}

private val LOG: Logger = logger<TraceDataCollectionConsentUI>()