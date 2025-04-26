// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.gdpr.ui.consents

import com.intellij.ide.gdpr.Consent
import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.util.text.StringUtil

internal class DefaultConsentUi(private val consent: Consent) : ConsentUi {
  override fun getCheckBoxText(): @NlsSafe String = StringUtil.capitalize(consent.name)

  override fun getCheckBoxCommentText(): @NlsSafe String = consent.text

  override fun getForcedState(): ConsentForcedState? = null
}