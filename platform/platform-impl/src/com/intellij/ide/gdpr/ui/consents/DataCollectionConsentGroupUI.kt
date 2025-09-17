// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.gdpr.ui.consents

import com.intellij.ide.IdeBundle
import com.intellij.ide.gdpr.DataCollectionAgreement
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.util.NlsSafe

internal data class DataCollectionConsentGroupUI(
  override val consentUis: List<ConsentUi>,
) : ConsentGroupUi {
  override val forcedStateDescription: @NlsSafe String? = run {
    val externalSettings = AiDataCollectionExternalSettings.findSettingsImplementedByAiAssistant()
    if (LOG.isDebugEnabled) {
      LOG.debug("AiDataCollectionExternalSettings: $externalSettings")
    }
    if (externalSettings != null) {
      val isForciblyDisabled = externalSettings.isForciblyDisabled()
      if (LOG.isDebugEnabled) {
        LOG.debug("AiDataCollectionExternalSettings: isForciblyDisabled: ${isForciblyDisabled}")
      }
      if (isForciblyDisabled) {
        return@run externalSettings.getForciblyDisabledDescription() ?: IdeBundle.message("gdpr.consent.externally.disabled.warning")
      }
    }
    val customerDetailedDataSharingAgreement = DataCollectionAgreement.getInstance()
    val forcedStateDescription = when (customerDetailedDataSharingAgreement) {
      DataCollectionAgreement.YES -> IdeBundle.message("gdpr.data.collection.consent.group.setting.enabled.warning.text")
      DataCollectionAgreement.NO -> IdeBundle.message("gdpr.data.collection.consent.group.setting.disabled.warning.text")
      DataCollectionAgreement.NOT_SET -> null
      else -> null
    }
    if (forcedStateDescription != null) {
      return@run forcedStateDescription
    }
    return@run null
  }

  override val commentText: @NlsSafe String = IdeBundle.message("gdpr.data.collection.consent.group.comment.text")
}

private val LOG: Logger = logger<DataCollectionConsentGroupUI>()