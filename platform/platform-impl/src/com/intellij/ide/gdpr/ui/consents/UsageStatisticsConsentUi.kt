// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.gdpr.ui.consents

import com.intellij.ide.ConsentOptionsProvider
import com.intellij.ide.IdeBundle
import com.intellij.ide.gdpr.Consent
import com.intellij.ide.gdpr.ConsentOptions
import com.intellij.ide.gdpr.ui.consents.ConsentForcedState.ExternallyDisabled
import com.intellij.internal.statistic.utils.StatisticsUploadAssistant
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.util.text.StringUtil

internal class UsageStatisticsConsentUi(private val consent: Consent) : ConsentUi {
  override fun getCheckBoxText(): @NlsSafe String {
    val checkBoxText = StringUtil.capitalize(consent.name)
    if (ConsentOptions.getInstance().isEAP) {
      return IdeBundle.message("gdpr.checkbox.when.using.eap.versions", checkBoxText)
    }
    return checkBoxText
  }

  override fun getCheckBoxCommentText(): @NlsSafe String = consent.text

  override fun getForcedState(): ConsentForcedState? {
    if (StatisticsUploadAssistant.isCollectionForceDisabled()) {
      return ExternallyDisabled(StatisticsUploadAssistant.getConsentWarning() ?: IdeBundle.message("gdpr.usage.statistics.disabled.externally.warning"))
    }
    if (isAllowedByFreeLicense()) {
      return ConsentForcedState.AlwaysEnabled(IdeBundle.message("gdpr.usage.statistics.enabled.for.free.license.warning"))
    }
    return null
  }

  private fun isAllowedByFreeLicense(): Boolean =
    ApplicationManager.getApplication()?.getService(ConsentOptionsProvider::class.java)?.isActivatedWithFreeLicense ?: false
}