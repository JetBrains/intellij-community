// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.gdpr.ui.consents

import com.intellij.ide.IdeBundle
import com.intellij.ide.gdpr.Consent
import com.intellij.ide.gdpr.ConsentOptions
import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.util.text.StringUtil

internal class ErrorsAutoReportConsentUi(private val consent: Consent) : ConsentUi {
  override fun getCheckBoxText(): @NlsSafe String {
    val checkBoxText = StringUtil.capitalize(consent.name)
    if (ConsentOptions.getInstance().isEAP) {
      return IdeBundle.message("gdpr.checkbox.when.using.eap.versions", checkBoxText)
    }
    return checkBoxText
  }

  override fun getCheckBoxCommentText(): @NlsSafe String = consent.text

  override fun getForcedState(): ConsentForcedState? = null
}