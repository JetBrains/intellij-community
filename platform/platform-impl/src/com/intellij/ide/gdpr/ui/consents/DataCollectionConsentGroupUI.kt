// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.gdpr.ui.consents

import com.intellij.ide.IdeBundle
import com.intellij.ide.gdpr.DataCollectionAgreement
import com.intellij.openapi.util.NlsSafe

internal class DataCollectionConsentGroupUI(
  consentsUI: List<ConsentUi>
): ConsentGroupUi {
  override val consentUis: List<ConsentUi> by lazy {
    consentsUI.sortedBy(ConsentUi::getCheckBoxText)
  }

  override val forcedStateDescription: @NlsSafe String? = run {
    val customerDetailedDataSharingAgreement = DataCollectionAgreement.getInstance() ?: return@run null
    val forcedStateDescription = when (customerDetailedDataSharingAgreement) {
      DataCollectionAgreement.YES -> IdeBundle.message("gdpr.data.collection.consent.group.setting.enabled.warning.text")
      DataCollectionAgreement.NO -> IdeBundle.message("gdpr.data.collection.consent.group.setting.disabled.warning.text")
      DataCollectionAgreement.NOT_SET -> null
    }
    if (forcedStateDescription != null) {
      return@run forcedStateDescription
    }
    val externalSettings = AiDataCollectionExternalSettings.findSettingsImplementedByAiAssistant()
    if (externalSettings != null && externalSettings.isForciblyDisabled()) {
      return@run externalSettings.getForciblyDisabledDescription() ?: IdeBundle.message("gdpr.consent.externally.disabled.warning")
    }
    return@run null
  }

  override val commentText: @NlsSafe String = IdeBundle.message("gdpr.data.collection.consent.group.comment.text")
}