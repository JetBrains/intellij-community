// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.gdpr.ui.consents

import com.intellij.openapi.util.NlsSafe

internal interface ConsentUi {
  fun getCheckBoxText(): @NlsSafe String

  fun getCheckBoxCommentText(): @NlsSafe String

  fun getForcedState(): ConsentForcedState?
}