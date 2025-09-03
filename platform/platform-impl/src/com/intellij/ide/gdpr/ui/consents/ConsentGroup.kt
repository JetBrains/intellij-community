// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.gdpr.ui.consents

import com.intellij.ide.gdpr.Consent
import com.intellij.ide.gdpr.ConsentOptions

internal class ConsentGroup(
  val id: String,
  consents: List<Consent>,
) {
  val consents: List<Consent> = consents.sortedByDescending { it.id }

  companion object {
    const val DATA_COLLECTION_GROUP_ID: String = "data.collection"

    val CONSENT_GROUP_MAPPING: Map<String, (Consent) -> Boolean> = mapOf(
      DATA_COLLECTION_GROUP_ID to { consent ->
        ConsentOptions.condUsageStatsConsent().test(consent) ||
        ConsentOptions.condTraceDataCollectionConsent().test(consent)
      }
    )
  }
}