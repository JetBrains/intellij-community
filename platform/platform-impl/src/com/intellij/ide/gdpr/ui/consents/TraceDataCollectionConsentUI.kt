// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.ide.gdpr.ui.consents

import com.intellij.ide.IdeBundle
import com.intellij.ide.gdpr.Consent
import com.intellij.ide.gdpr.DataCollectionAgreement
import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.util.text.StringUtil

internal class TraceDataCollectionConsentUI(
  private val consent: Consent,
) : ConsentUi {
  override fun getCheckBoxText(): @NlsSafe String = StringUtil.capitalize(consent.name)

  override fun getCheckBoxCommentText(): @NlsSafe String = consent.text

  override fun getForcedState(): ConsentForcedState? {
    val dataCollectionAgreement = DataCollectionAgreement.getInstance() ?: return null
    return when (dataCollectionAgreement) {
      DataCollectionAgreement.YES -> ConsentForcedState.AlwaysEnabled(null)
      DataCollectionAgreement.NOT_SET -> null
      DataCollectionAgreement.NO -> ConsentForcedState.ExternallyDisabled(IdeBundle.message("gdpr.data.collection.consent.group.setting.disabled.warning.text"))
    }
  }
}