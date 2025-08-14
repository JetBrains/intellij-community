// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.gdpr.ui.consents

import com.intellij.ide.IdeBundle
import com.intellij.ide.gdpr.DataCollectionAgreement
import com.intellij.openapi.util.NlsSafe

internal data class DataCollectionConsentGroupUI(
  override val consentUis: List<ConsentUi>,
) : ConsentGroupUi {
  override val forcedStateDescription: @NlsSafe String? = run {
    val customerDetailedDataSharingAgreement = DataCollectionAgreement.getInstance() ?: return@run null
    when (customerDetailedDataSharingAgreement) {
      DataCollectionAgreement.YES -> IdeBundle.message("gdpr.data.collection.consent.group.setting.enabled.warning.text")
      DataCollectionAgreement.NO -> null
      DataCollectionAgreement.NOT_SET -> null
    }
  }

  override val commentText: @NlsSafe String = IdeBundle.message("gdpr.data.collection.consent.group.comment.text")
}