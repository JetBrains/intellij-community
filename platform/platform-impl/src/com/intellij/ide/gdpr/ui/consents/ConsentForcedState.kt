// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.gdpr.ui.consents

import com.intellij.openapi.util.NlsSafe

internal sealed interface ConsentForcedState {
  val description: @NlsSafe String

  data class ExternallyDisabled(override val description: @NlsSafe String) : ConsentForcedState
  data class AlwaysEnabled(override val description: @NlsSafe String) : ConsentForcedState
}